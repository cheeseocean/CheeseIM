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
	cfg          Config
	httpClient   *httpapi.Client
	tcpClient    *tcpim.Client
	authService  *auth.AuthService
	social       *social.RosterService
	sync         syncService
	events       chan types.Event
	accessToken  string
	currentUser  string
	stopRealtime chan struct{}
}

type syncService interface {
	Bootstrap(data types.BootstrapData)
	Reset(currentUserID string)
	OpenConversation(ctx context.Context, accessToken, conversationID string, limit int) ([]types.Message, error)
	HandleRealtimeMessage(ctx context.Context, accessToken string, message types.Message) (string, []types.Message, bool, error)
	MarkRead(ctx context.Context, accessToken, conversationID string, readSeq int64) (types.ReadSnapshot, error)
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

func (c *Client) OpenConversation(ctx context.Context, conversationID string, limit int) ([]types.Message, error) {
	if c.sync == nil {
		return nil, fmt.Errorf("sdk client not initialized")
	}
	return c.sync.OpenConversation(ctx, c.accessToken, conversationID, limit)
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

func (c *Client) MarkRead(ctx context.Context, conversationID string, readSeq int64) (types.ReadSnapshot, error) {
	if c.sync == nil {
		return types.ReadSnapshot{}, fmt.Errorf("sdk client not initialized")
	}
	snapshot, err := c.sync.MarkRead(ctx, c.accessToken, conversationID, readSeq)
	if err == nil {
		c.emit(types.Event{Kind: types.EventKindReadUpdated, ConversationID: conversationID, ReadSnapshot: &snapshot})
	}
	return snapshot, err
}

func (c *Client) bootstrap(ctx context.Context, session auth.AuthSession) (types.BootstrapData, error) {
	data, err := c.social.LoadInitialData(ctx, session.AccessToken)
	if err != nil {
		return types.BootstrapData{}, err
	}
	c.accessToken = session.AccessToken
	c.currentUser = session.UserID
	if c.sync == nil {
		c.sync = imsync.NewService(c.social, session.UserID)
	} else {
		c.sync.Reset(session.UserID)
	}
	c.sync.Bootstrap(data)
	c.restartRealtimeLoop()
	return data, nil
}

func (c *Client) restartRealtimeLoop() {
	if c.stopRealtime != nil {
		close(c.stopRealtime)
	}
	c.stopRealtime = make(chan struct{})
	go c.realtimeLoop(c.stopRealtime)
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
		if event.Message == nil || c.sync == nil {
			return
		}
		message := toSDKMessage(event.Message)
		c.emit(types.Event{Kind: types.EventKindSyncStarted})
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		conversationID, messages, repaired, err := c.sync.HandleRealtimeMessage(ctx, c.accessToken, message)
		if err != nil {
			c.emit(types.Event{Kind: types.EventKindError, Err: err})
			return
		}
		if len(messages) == 0 {
			return
		}
		last := messages[len(messages)-1]
		c.emit(types.Event{
			Kind:           types.EventKindRealtime,
			RequestID:      event.RequestID,
			ConversationID: conversationID,
			Message:        &last,
		})
		if repaired {
			c.emit(types.Event{Kind: types.EventKindGapRepaired, ConversationID: conversationID})
		}
		c.emit(types.Event{Kind: types.EventKindSyncCompleted, ConversationID: conversationID})
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
