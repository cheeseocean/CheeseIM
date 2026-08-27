package client

import (
	"context"
	"fmt"
	"strings"
	"sync"
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
	sessionMu   sync.RWMutex
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
	c.sessionMu.RLock()
	defer c.sessionMu.RUnlock()
	return c.currentUser
}

func (c *Client) Login(ctx context.Context, userID, identityAssertion string) (types.BootstrapData, error) {
	session, err := c.authService.Login(ctx, userID, identityAssertion, c.cfg.DeviceID, c.cfg.Platform, c.cfg.TCPAddr)
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
	session, err := c.authService.Reconnect(ctx, c.accessTokenSnapshot(), c.cfg.DeviceID, c.cfg.Platform, c.cfg.TCPAddr)
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

// GetServerMaxSeq 获取服务端最大序列号快照
func (c *Client) GetServerMaxSeq(conversationID string) int64 {
	if c.sync == nil {
		return 0
	}
	return c.sync.GetServerMaxSeq(conversationID)
}

// UpdateSyncedMaxSeq 更新已同步的最大序列号
func (c *Client) UpdateSyncedMaxSeq(conversationID string, seq int64) {
	if c.sync == nil {
		return
	}
	c.sync.UpdateSyncedMaxSeq(conversationID, seq)
}

// GetConversationCursor 获取会话元数据同步游标。
func (c *Client) GetConversationCursor() types.ConversationSyncCursor {
	if c.sync == nil {
		return types.ConversationSyncCursor{}
	}
	return c.sync.GetConversationCursor()
}

// UpdateConversationCursor 设置会话元数据同步游标。
func (c *Client) UpdateConversationCursor(cursor types.ConversationSyncCursor) {
	if c.sync == nil {
		c.sync = imsync.NewService(c.social)
	}
	c.sync.UpdateConversationCursor(cursor)
}

// SyncConversations 同步会话元数据增量并更新 SDK 本地游标。
func (c *Client) SyncConversations(ctx context.Context) (types.ConversationSyncResult, error) {
	if c.sync == nil {
		return types.ConversationSyncResult{}, fmt.Errorf("sdk client not initialized")
	}
	result, err := c.social.SyncConversations(ctx, c.accessTokenSnapshot(), c.sync.GetConversationCursor())
	if err != nil {
		return types.ConversationSyncResult{}, err
	}
	c.sync.UpdateConversationCursor(result.ConversationSyncCursor)
	return result, nil
}

// DeleteConversation 删除当前用户维度的会话元数据；历史消息仍保留在服务端。
func (c *Client) DeleteConversation(ctx context.Context, conversationID string) error {
	token := c.accessTokenSnapshot()
	if token == "" {
		return fmt.Errorf("sdk client not initialized")
	}
	return c.social.DeleteConversation(ctx, token, conversationID)
}

// OpenConversation 打开会话，拉取历史消息（降级实现，供参考）
func (c *Client) OpenConversation(ctx context.Context, conversationID string, limit int) ([]types.Message, error) {
	if c.sync == nil {
		return nil, fmt.Errorf("sdk client not initialized")
	}
	serverMax := c.sync.GetServerMaxSeq(conversationID)
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
	currentUser := c.CurrentUserID()
	receiverID, groupID, chatType, err := resolveChatTarget(conversationID, currentUser)
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
		SenderID:    currentUser,
		ReceiverID:  receiverID,
		GroupID:     groupID,
		ChatType:    chatType,
		ContentType: 101,
		Content:     []byte(text),
		SendTime:    message.GetSendTime(),
	}, nil
}

func (c *Client) AddFriend(ctx context.Context, friendUserID, message string) error {
	return c.httpClient.AddFriend(ctx, c.accessTokenSnapshot(), friendUserID, message)
}

func (c *Client) ListIncomingFriendRequests(ctx context.Context) ([]types.FriendRequest, error) {
	return c.httpClient.ListIncomingFriendRequests(ctx, c.accessTokenSnapshot())
}

func (c *Client) ListFriends(ctx context.Context) ([]types.Friend, error) {
	return c.httpClient.ListFriends(ctx, c.accessTokenSnapshot())
}

func (c *Client) ListOutgoingFriendRequests(ctx context.Context) ([]types.FriendRequest, error) {
	return c.httpClient.ListOutgoingFriendRequests(ctx, c.accessTokenSnapshot())
}

func (c *Client) AcceptFriendRequest(ctx context.Context, friendUserID string) error {
	return c.httpClient.AcceptFriendRequest(ctx, c.accessTokenSnapshot(), friendUserID)
}

func (c *Client) RejectFriendRequest(ctx context.Context, friendUserID string) error {
	return c.httpClient.RejectFriendRequest(ctx, c.accessTokenSnapshot(), friendUserID)
}

func (c *Client) CancelFriendRequest(ctx context.Context, friendUserID string) error {
	return c.httpClient.CancelFriendRequest(ctx, c.accessTokenSnapshot(), friendUserID)
}

func (c *Client) MarkRead(ctx context.Context, conversationID string, readSeq int64) error {
	if c.sync == nil {
		return fmt.Errorf("sdk client not initialized")
	}
	return c.sync.MarkRead(ctx, conversationID, readSeq)
}

// AckDelivered confirms a per-device high watermark after the application has accepted the message locally.
func (c *Client) AckDelivered(conversationID string, deliveredSeq int64) error {
	if conversationID == "" || deliveredSeq <= 0 {
		return fmt.Errorf("conversationID and positive deliveredSeq required")
	}
	opID := fmt.Sprintf("d%015x", time.Now().UnixNano()&0x0fffffffffffffff)
	return c.tcpClient.AckDelivery(opID, &pb.ProtoChatDeliveryAckCommand{
		ConversationId:  conversationID,
		MaxDeliveredSeq: deliveredSeq,
		DeviceId:        c.cfg.DeviceID,
		OpId:            opID,
	})
}

// RevokeMessage requests revocation of a server message in the given conversation.
func (c *Client) RevokeMessage(conversationID, serverMsgID, reason string) error {
	if conversationID == "" || serverMsgID == "" {
		return fmt.Errorf("conversationID and serverMsgID required")
	}
	opID := fmt.Sprintf("r%015x", time.Now().UnixNano()&0x0fffffffffffffff)
	return c.tcpClient.RevokeMessage(opID, &pb.ProtoChatRevokeCommand{
		ConversationId: conversationID,
		ServerMsgId:    serverMsgID,
		OpId:           opID,
		Reason:         reason,
	})
}

// SendTyping publishes a best-effort typing state. START uses a short server-clamped TTL.
func (c *Client) SendTyping(conversationID string, action types.TypingAction) error {
	if conversationID == "" || (action != types.TypingActionStart && action != types.TypingActionStop) {
		return fmt.Errorf("conversationID and valid typing action required")
	}
	opID := fmt.Sprintf("t%015x", time.Now().UnixNano()&0x0fffffffffffffff)
	return c.tcpClient.SendTyping(opID, &pb.ProtoChatTypingCommand{
		ConversationId: conversationID,
		Action:         int32(action),
		TtlSeconds:     4,
	})
}

func (c *Client) bootstrap(ctx context.Context, session auth.AuthSession) (types.BootstrapData, error) {
	data, err := c.social.LoadInitialData(ctx, session.AccessToken)
	if err != nil {
		return types.BootstrapData{}, err
	}
	c.sessionMu.Lock()
	c.accessToken = session.AccessToken
	c.currentUser = session.UserID
	c.sessionMu.Unlock()
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
		if event.Ack == nil {
			return
		}
		c.emit(types.Event{Kind: types.EventKindAck, RequestID: event.RequestID, SendAck: &types.SendAck{
			ClientMsgID: event.Ack.GetClientMsgId(), ServerMsgID: event.Ack.GetServerMsgId(),
			AcceptedAt: event.Ack.GetAcceptedAt(), AcceptedState: int32(event.Ack.GetAcceptedState()),
		}})
	case tcpim.EventDelivery:
		if event.Delivery == nil {
			return
		}
		c.emit(types.Event{Kind: types.EventKindDeliveryUpdated, RequestID: event.RequestID,
			ConversationID: event.Delivery.GetConversationId(), Delivery: &types.DeliveryUpdate{
				ConversationID: event.Delivery.GetConversationId(), RecipientID: event.Delivery.GetRecipientId(),
				DeviceID: event.Delivery.GetDeviceId(), DeliveredSeq: event.Delivery.GetDeliveredSeq(),
				UpdatedAt: event.Delivery.GetUpdatedAt(),
			}})
	case tcpim.EventRead:
		if event.Read == nil {
			return
		}
		c.emit(types.Event{Kind: types.EventKindReadUpdated, RequestID: event.RequestID,
			ConversationID: event.Read.GetConversationId(), Read: &types.ReadUpdate{
				ConversationID: event.Read.GetConversationId(), ReaderID: event.Read.GetReaderId(),
				ReadSeq: event.Read.GetReadSeq(), UpdatedAt: event.Read.GetUpdatedAt(),
			}})
	case tcpim.EventRevoke:
		if event.Revoke == nil {
			return
		}
		c.emit(types.Event{Kind: types.EventKindRevokeUpdated, RequestID: event.RequestID,
			ConversationID: event.Revoke.GetConversationId(), Revoke: &types.RevokeUpdate{
				ConversationID: event.Revoke.GetConversationId(), ServerMsgID: event.Revoke.GetServerMsgId(),
				OperatorUserID: event.Revoke.GetOperatorUserId(), OperatorName: event.Revoke.GetOperatorName(),
				TargetSenderID: event.Revoke.GetTargetSenderId(), TargetSenderName: event.Revoke.GetTargetSenderName(),
				RevokedAt: event.Revoke.GetRevokedAt(), MutationVersion: event.Revoke.GetMutationVersion(),
			}})
	case tcpim.EventTyping:
		if event.Typing == nil {
			return
		}
		c.emit(types.Event{Kind: types.EventKindTypingUpdated, RequestID: event.RequestID,
			ConversationID: event.Typing.GetConversationId(), Typing: &types.TypingUpdate{
				ConversationID: event.Typing.GetConversationId(), SenderID: event.Typing.GetSenderId(),
				Action: types.TypingAction(event.Typing.GetAction()), ExpiresAt: event.Typing.GetExpiresAt(),
			}})
	case tcpim.EventRoster:
		c.emit(types.Event{Kind: types.EventKindRosterUpdated, RequestID: event.RequestID})
	case tcpim.EventForceLogout:
		if event.ForceLogout == nil {
			return
		}
		c.clearSession()
		if c.tcpClient != nil {
			_ = c.tcpClient.Close()
		}
		c.emit(types.Event{Kind: types.EventKindForcedLogout, RequestID: event.RequestID, ForceLogout: &types.ForceLogout{
			Reason: event.ForceLogout.GetReason(), SessionID: event.ForceLogout.GetSessionId(),
			DeviceID: event.ForceLogout.GetDeviceId(), OccurredAt: event.ForceLogout.GetOccurredAt(),
		}})
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

func (c *Client) accessTokenSnapshot() string {
	c.sessionMu.RLock()
	defer c.sessionMu.RUnlock()
	return c.accessToken
}

func (c *Client) clearSession() {
	c.sessionMu.Lock()
	c.accessToken = ""
	c.currentUser = ""
	c.sessionMu.Unlock()
	if c.sync != nil {
		c.sync.SetAccessToken("")
		c.sync.Reset()
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
