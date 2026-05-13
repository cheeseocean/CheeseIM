package client

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/cheeseim/cheeseim-go-sdk/auth"
	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
	"github.com/cheeseim/cheeseim-go-sdk/social"
	imsync "github.com/cheeseim/cheeseim-go-sdk/sync"
	"github.com/cheeseim/cheeseim-go-sdk/transport/httpapi"
	"github.com/cheeseim/cheeseim-go-sdk/transport/tcpim"
	"github.com/cheeseim/cheeseim-go-sdk/types"
)

type Config struct {
	APIBaseURL string
	TCPAddr    string
	DeviceID   string
	Platform   string
	Timeout    time.Duration
}

type Client struct {
	cfg         Config
	httpClient  *httpapi.Client
	tcpClient   *tcpim.Client
	authService *auth.AuthService
	social      *social.RosterService
	sync        *imsync.Service
	events      chan types.Event
	accessToken string
	currentUser string
	stopChan    chan struct{}
}

func New(cfg Config) *Client {
	httpClient := httpapi.New(cfg.APIBaseURL, cfg.Timeout)
	tcpClient := tcpim.NewClient(nil, 30*time.Second)
	authService := auth.NewAuthService(httpClient, httpClient, tcpClient)
	return &Client{
		cfg:         cfg,
		httpClient:  httpClient,
		tcpClient:   tcpClient,
		authService: authService,
		social:      social.NewRosterService(httpClient),
		events:      make(chan types.Event, 32),
	}
}

func (c *Client) Events() <-chan types.Event {
	return c.events
}

func (c *Client) CurrentUserID() string {
	return c.currentUser
}

func (c *Client) Login(ctx context.Context, userID, password string) (types.BootstrapData, error) {
	session, err := c.authService.Login(ctx, userID, password, c.cfg.DeviceID, c.cfg.Platform, c.cfg.TCPAddr)
	if err != nil {
		return types.BootstrapData{}, err
	}
	return c.bootstrap(ctx, session)
}

func (c *Client) LoginWithToken(ctx context.Context, accessToken string) (types.BootstrapData, error) {
	session, err := c.authService.LoginWithToken(ctx, accessToken, c.cfg.DeviceID, c.cfg.Platform, c.cfg.TCPAddr)
	if err != nil {
		return types.BootstrapData{}, err
	}
	return c.bootstrap(ctx, session)
}

func (c *Client) Reconnect(ctx context.Context) (types.BootstrapData, error) {
	session, err := c.authService.Reconnect(ctx, c.accessToken, c.cfg.DeviceID, c.cfg.Platform, c.cfg.TCPAddr)
	if err != nil {
		return types.BootstrapData{}, err
	}
	return c.bootstrap(ctx, session)
}

// PullMessages 拉取消息，由应用层调用
func (c *Client) PullMessages(ctx context.Context, ranges []types.SeqRange, limitPerConversation int64) ([]types.PulledConversationMessages, error) {
	if c.sync == nil {
		return nil, fmt.Errorf("sdk client not initialized")
	}
	return c.sync.PullMessages(ctx, ranges, limitPerConversation)
}

// GetSyncedMaxSeq 获取已同步的最大序列号
func (c *Client) GetSyncedMaxSeq(conversationID string) int64 {
	if c.sync == nil {
		return 0
	}
	return c.sync.GetSyncedMaxSeq(conversationID)
}

// UpdateSyncedMaxSeq 更新已同步的最大序列号
func (c *Client) UpdateSyncedMaxSeq(conversationID string, seq int64) {
	if c.sync == nil {
		return
	}
	c.sync.UpdateSyncedMaxSeq(conversationID, seq)
}

// OpenConversation 打开会话，拉取历史消息（降级实现，供参考）
func (c *Client) OpenConversation(ctx context.Context, conversationID string, limit int) ([]types.Message, error) {
	if c.sync == nil {
		return nil, fmt.Errorf("sdk client not initialized")
	}
	serverMax := c.sync.GetSyncedMaxSeq(conversationID)
	beginSeq := serverMax - int64(limit) + 1
	if beginSeq < 1 {
		beginSeq = 1
	}
	ranges := []types.SeqRange{
		{ConversationID: conversationID, BeginSeq: beginSeq, EndSeq: serverMax},
	}
	pulled, err := c.sync.PullMessages(ctx, ranges, int64(limit))
	if err != nil {
		return nil, err
	}
	for _, conv := range pulled {
		if conv.ConversationID == conversationID {
			return conv.Messages, nil
		}
	}
	return nil, nil
}

func (c *Client) SendText(requestID, conversationID, text string) (types.Message, error) {
	receiverID, groupID, chatType, err := resolveChatTarget(conversationID, c.currentUser)
	if err != nil {
		return types.Message{}, err
	}
	message := &pb.ProtoMessage{
		ClientMsgId: requestID,
		ReceiverId:  receiverID,
		GroupId:     groupID,
		Content:     []byte(text),
		ContentType: 101,
		ChatType:    chatType,
		SendTime:    time.Now().UnixMilli(),
	}
	if err := c.tcpClient.SendChatMessage(requestID, message); err != nil {
		return types.Message{}, err
	}
	return types.Message{
		ClientMsgID: requestID,
		SenderID:    c.currentUser,
		ReceiverID:  receiverID,
		GroupID:     groupID,
		ChatType:    chatType,
		ContentType: 101,
		Content:     []byte(text),
		SendTime:    message.GetSendTime(),
	}, nil
}

func (c *Client) AddFriend(ctx context.Context, friendUserID, message string) error {
	return c.httpClient.AddFriend(ctx, c.accessToken, friendUserID, message)
}

func (c *Client) MarkRead(ctx context.Context, conversationID string, readSeq int64) error {
	if c.sync == nil {
		return fmt.Errorf("sdk client not initialized")
	}
	return c.sync.MarkRead(ctx, conversationID, readSeq)
}

func (c *Client) bootstrap(ctx context.Context, session auth.AuthSession) (types.BootstrapData, error) {
	data, err := c.social.LoadInitialData(ctx, session.AccessToken)
	if err != nil {
		return types.BootstrapData{}, err
	}
	c.accessToken = session.AccessToken
	c.currentUser = session.UserID
	if c.sync == nil {
		c.sync = imsync.NewService(c.social)
	} else {
		c.sync.Reset()
	}
	c.sync.SetAccessToken(session.AccessToken)
	c.sync.Bootstrap(data)
	c.restartRealtimeLoop()
	return data, nil
}

func (c *Client) restartRealtimeLoop() {
	if c.stopChan != nil {
		close(c.stopChan)
	}
	c.stopChan = make(chan struct{})
	go c.realtimeLoop(c.stopChan)
}

func (c *Client) realtimeLoop(stop <-chan struct{}) {
	events := c.authService.Events()
	if events == nil {
		return
	}
	for {
		select {
		case <-stop:
			return
		case event, ok := <-events:
			if !ok {
				c.emit(types.Event{Kind: types.EventKindDisconnected})
				return
			}
			c.handleTransportEvent(event)
		}
	}
}

func (c *Client) handleTransportEvent(event tcpim.Event) {
	switch event.Kind {
	case tcpim.EventAck:
		c.emit(types.Event{Kind: types.EventKindAck, RequestID: event.RequestID})
	case tcpim.EventDisconnect:
		c.emit(types.Event{Kind: types.EventKindDisconnected})
	case tcpim.EventError:
		c.emit(types.Event{Kind: types.EventKindError, Err: event.Err})
	case tcpim.EventMessage:
		// 直接把收到的消息转发给应用层，不做任何业务处理
		if event.Message == nil {
			return
		}
		message := toSDKMessage(event.Message)
		conversationID := resolveConversationID(message)
		c.emit(types.Event{
			Kind:           types.EventKindRealtime,
			RequestID:      event.RequestID,
			ConversationID: conversationID,
			Message:        &message,
		})
	}
}

func (c *Client) emit(event types.Event) {
	select {
	case c.events <- event:
	default:
	}
}

func toSDKMessage(message *pb.ProtoMessage) types.Message {
	return types.Message{
		Sequence:    message.GetSeq(),
		ClientMsgID: message.GetClientMsgId(),
		ServerMsgID: message.GetServerMsgId(),
		SenderID:    message.GetSenderId(),
		ReceiverID:  message.GetReceiverId(),
		GroupID:     message.GetGroupId(),
		ContentType: message.GetContentType(),
		ChatType:    message.GetChatType(),
		Content:     message.GetContent(),
		SendTime:    message.GetSendTime(),
		CreateTime:  message.GetCreateTime(),
		Status:      message.GetStatus(),
		Platform:    message.GetPlatformCode(),
		UniqueID:    message.GetUniqueId(),
		Source:      message.GetSource(),
		Attributes:  message.GetAttributes(),
	}
}

func resolveChatTarget(conversationID, currentUserID string) (string, string, int32, error) {
	switch {
	case strings.HasPrefix(conversationID, "g:"):
		return "", conversationID[2:], 2, nil
	case strings.HasPrefix(conversationID, "s:"):
		parts := strings.Split(conversationID, ":")
		if len(parts) != 3 {
			return "", "", 0, fmt.Errorf("invalid direct conversation: %s", conversationID)
		}
		a, b := parts[1], parts[2]
		if a == currentUserID {
			return b, "", 1, nil
		}
		if b == currentUserID {
			return a, "", 1, nil
		}
		return b, "", 1, nil
	default:
		return "", "", 0, fmt.Errorf("unsupported conversation: %s", conversationID)
	}
}

// resolveConversationID 从消息中解析会话 ID
func resolveConversationID(message types.Message) string {
	switch message.ChatType {
	case 2:
		if message.GroupID != "" {
			return "g:" + message.GroupID
		}
	case 1:
		if message.SenderID != "" && message.ReceiverID != "" {
			if message.SenderID <= message.ReceiverID {
				return "s:" + message.SenderID + ":" + message.ReceiverID
			}
			return "s:" + message.ReceiverID + ":" + message.SenderID
		}
	}
	return ""
}
