package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"

	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
	"github.com/cheeseim/cheeseim-go-sdk/types"
	"google.golang.org/protobuf/encoding/protojson"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

func New(baseURL string, timeout time.Duration) *Client {
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

func (c *Client) IssueWsTicket(ctx context.Context, accessToken, deviceID, platform string) (types.WsTicket, error) {
	body := map[string]string{
		"device_id":      deviceID,
		"platform":       platform,
		"client_version": "CheeseBox/dev",
	}
	var response struct {
		Ticket   string `json:"ticket"`
		ExpireAt int64  `json:"expire_at"`
		WSURL    string `json:"ws_url"`
	}
	if err := c.doJSON(ctx, http.MethodPost, "/api/im/ws-ticket", accessToken, body, &response); err != nil {
		return types.WsTicket{}, err
	}
	return types.WsTicket{
		Ticket:   response.Ticket,
		ExpireAt: response.ExpireAt,
		WSURL:    response.WSURL,
	}, nil
}

func (c *Client) Login(ctx context.Context, userID, identityAssertion string, platformID int, deviceID, clientVersion string) (string, error) {
	body := map[string]any{
		"userId":            userID,
		"identityAssertion": identityAssertion,
		"platformId":        platformID,
		"deviceId":          deviceID,
		"clientVersion":     clientVersion,
	}
	var response struct {
		AccessToken string `json:"accessToken"`
	}
	if err := c.doJSON(ctx, http.MethodPost, "/api/auth/login", "", body, &response); err != nil {
		return "", err
	}
	return response.AccessToken, nil
}

func (c *Client) ListFriends(ctx context.Context, accessToken string) ([]types.Friend, error) {
	var response []json.RawMessage
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/friends", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Friend, 0, len(response))
	for _, raw := range response {
		item := &pb.ProtoFriend{}
		if err := protojson.Unmarshal(raw, item); err != nil {
			return nil, fmt.Errorf("decode friend: %w", err)
		}
		items = append(items, types.Friend{
			UserID:      item.GetFriendId(),
			DisplayName: item.GetRemark(),
		})
	}
	return items, nil
}

func (c *Client) ListConversations(ctx context.Context, accessToken string) ([]types.Conversation, error) {
	var response []json.RawMessage
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/conversations", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Conversation, 0, len(response))
	for _, raw := range response {
		item := &pb.ProtoConversation{}
		if err := protojson.Unmarshal(raw, item); err != nil {
			return nil, fmt.Errorf("decode conversation: %w", err)
		}
		items = append(items, toConversation(item))
	}
	return items, nil
}

func (c *Client) ListGroups(ctx context.Context, accessToken string) ([]types.Group, error) {
	var response []json.RawMessage
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/groups", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Group, 0, len(response))
	for _, raw := range response {
		item := &pb.ProtoGroupSummary{}
		if err := protojson.Unmarshal(raw, item); err != nil {
			return nil, fmt.Errorf("decode group: %w", err)
		}
		items = append(items, types.Group{GroupID: item.GetGroupId(), GroupName: item.GetGroupName(), AvatarURL: item.GetAvatarUrl()})
	}
	return items, nil
}

func (c *Client) GetConversationMaxSeqs(ctx context.Context, accessToken string) ([]types.ReadSnapshot, error) {
	var response []struct {
		ConversationID string `json:"conversationId"`
		MaxSeq         int64  `json:"maxSeq"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/conversations/max-seqs", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.ReadSnapshot, 0, len(response))
	for _, item := range response {
		items = append(items, types.ReadSnapshot{
			ConversationID: item.ConversationID,
			MaxSeq:         item.MaxSeq,
		})
	}
	return items, nil
}

func (c *Client) GetConversationReadSnapshots(ctx context.Context, accessToken string) ([]types.ReadSnapshot, error) {
	var response []struct {
		ConversationID string `json:"conversationId"`
		ReadSeq        int64  `json:"readSeq"`
		MaxSeq         int64  `json:"maxSeq"`
		UnreadCount    int64  `json:"unreadCount"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/conversations/read-snapshots", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.ReadSnapshot, 0, len(response))
	for _, item := range response {
		items = append(items, types.ReadSnapshot(item))
	}
	return items, nil
}

func (c *Client) SyncConversations(ctx context.Context, accessToken string, cursor types.ConversationSyncCursor) (types.ConversationSyncResult, error) {
	values := url.Values{}
	values.Set("versionId", cursor.VersionID)
	values.Set("version", fmt.Sprintf("%d", cursor.Version))
	values.Set("idHash", fmt.Sprintf("%d", cursor.IDHash))
	path := "/api/im/conversations/sync/incremental?" + values.Encode()
	var raw json.RawMessage
	if err := c.doJSON(ctx, http.MethodGet, path, accessToken, nil, &raw); err != nil {
		return types.ConversationSyncResult{}, err
	}
	response := &pb.ProtoConversationSyncResult{}
	if err := protojson.Unmarshal(raw, response); err != nil {
		return types.ConversationSyncResult{}, fmt.Errorf("decode conversation sync: %w", err)
	}
	result := types.ConversationSyncResult{
		ConversationSyncCursor: types.ConversationSyncCursor{
			VersionID: response.GetVersionId(),
			Version:   response.GetVersion(),
			IDHash:    response.GetIdHash(),
		},
		Full:   response.GetFull(),
		Delete: response.GetDelete(),
	}
	for _, item := range response.GetInsert() {
		result.Insert = append(result.Insert, toConversation(item))
	}
	for _, item := range response.GetUpdate() {
		result.Update = append(result.Update, toConversation(item))
	}
	return result, nil
}

func (c *Client) SyncControlEvents(ctx context.Context, accessToken string, cursor int64, limit int) (types.ControlEventSyncResult, error) {
	values := url.Values{}
	values.Set("cursor", fmt.Sprintf("%d", cursor))
	values.Set("limit", fmt.Sprintf("%d", limit))
	path := "/api/im/conversations/control-events?" + values.Encode()
	var response struct {
		Events []struct {
			EventID        string `json:"eventId"`
			Cursor         int64  `json:"cursor"`
			ConversationID string `json:"conversationId"`
			Type           int    `json:"type"`
			Payload        string `json:"payload"`
			CreatedAt      int64  `json:"createdAt"`
			ExpiresAt      int64  `json:"expiresAt"`
		} `json:"events"`
		NextCursor int64 `json:"nextCursor"`
		HasMore    bool  `json:"hasMore"`
	}
	if err := c.doJSON(ctx, http.MethodGet, path, accessToken, nil, &response); err != nil {
		return types.ControlEventSyncResult{}, err
	}
	result := types.ControlEventSyncResult{NextCursor: response.NextCursor, HasMore: response.HasMore}
	for _, item := range response.Events {
		event := types.ControlEvent{EventID: item.EventID, Cursor: item.Cursor, ConversationID: item.ConversationID,
			Type: types.ControlEventType(item.Type), Payload: json.RawMessage(item.Payload), CreatedAt: item.CreatedAt, ExpiresAt: item.ExpiresAt}
		if err := decodeControlEventPayload(&event); err != nil {
			return types.ControlEventSyncResult{}, fmt.Errorf("decode control event %s: %w", item.EventID, err)
		}
		result.Events = append(result.Events, event)
	}
	return result, nil
}

func decodeControlEventPayload(event *types.ControlEvent) error {
	switch event.Type {
	case types.ControlEventReadAdvanced:
		return json.Unmarshal(event.Payload, &event.Read)
	case types.ControlEventMessageRevoked:
		return json.Unmarshal(event.Payload, &event.Revoke)
	case types.ControlEventDeliveryAdvanced:
		return json.Unmarshal(event.Payload, &event.Delivery)
	default:
		return nil
	}
}

func (c *Client) DeleteConversation(ctx context.Context, accessToken, conversationID string) error {
	path := "/api/im/conversations/" + url.PathEscape(conversationID)
	return c.doJSON(ctx, http.MethodDelete, path, accessToken, nil, nil)
}

func (c *Client) PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limitPerConversation int64) ([]types.PulledConversationMessages, error) {
	body := map[string]any{
		"limitPerConversation": limitPerConversation,
	}
	rangeItems := make([]map[string]any, 0, len(ranges))
	for _, item := range ranges {
		rangeItems = append(rangeItems, map[string]any{
			"conversationId": item.ConversationID,
			"beginSeq":       item.BeginSeq,
			"endSeq":         item.EndSeq,
		})
	}
	body["ranges"] = rangeItems
	var response struct {
		Conversations []struct {
			ConversationID string `json:"conversationId"`
			EndSeq         int64  `json:"endSeq"`
			Completed      bool   `json:"completed"`
			Messages       []struct {
				Sequence    int64             `json:"seq"`
				ClientMsgID string            `json:"clientMsgId"`
				ServerMsgID string            `json:"serverMsgId"`
				SenderID    string            `json:"senderId"`
				SenderName  string            `json:"senderNickName"`
				ReceiverID  string            `json:"receiverId"`
				GroupID     string            `json:"groupId"`
				ContentType int32             `json:"contentType"`
				ChatType    int32             `json:"chatType"`
				Content     []byte            `json:"content"`
				SendTime    int64             `json:"sendTime"`
				CreateTime  int64             `json:"createTime"`
				Status      int32             `json:"status"`
				Platform    int32             `json:"platformType"`
				UniqueID    string            `json:"uniqueId"`
				Source      int32             `json:"source"`
				Attributes  map[string]string `json:"attributes"`
			} `json:"messages"`
		} `json:"conversations"`
	}
	if err := c.doJSON(ctx, http.MethodPost, "/api/im/conversations/sync/pull", accessToken, body, &response); err != nil {
		return nil, err
	}
	items := make([]types.PulledConversationMessages, 0, len(response.Conversations))
	for _, conv := range response.Conversations {
		messages := make([]types.Message, 0, len(conv.Messages))
		for _, msg := range conv.Messages {
			messages = append(messages, types.Message{
				Sequence:    msg.Sequence,
				ClientMsgID: msg.ClientMsgID,
				ServerMsgID: msg.ServerMsgID,
				SenderID:    msg.SenderID,
				SenderName:  msg.SenderName,
				ReceiverID:  msg.ReceiverID,
				GroupID:     msg.GroupID,
				ContentType: msg.ContentType,
				ChatType:    msg.ChatType,
				Content:     msg.Content,
				SendTime:    msg.SendTime,
				CreateTime:  msg.CreateTime,
				Status:      msg.Status,
				Platform:    msg.Platform,
				UniqueID:    msg.UniqueID,
				Source:      msg.Source,
				Attributes:  msg.Attributes,
			})
		}
		items = append(items, types.PulledConversationMessages{
			ConversationID: conv.ConversationID,
			EndSeq:         conv.EndSeq,
			Completed:      conv.Completed,
			Messages:       messages,
		})
	}
	return items, nil
}

func (c *Client) AckReadSeq(ctx context.Context, accessToken, conversationID string, readSeq int64) error {
	path := fmt.Sprintf("/api/im/conversations/%s/read-seq", conversationID)
	return c.doJSON(ctx, http.MethodPut, path, accessToken, map[string]any{
		"readSeq": readSeq,
	}, nil)
}

func (c *Client) AddFriend(ctx context.Context, accessToken, friendUserID, message string) error {
	body, err := protojson.Marshal(&pb.ProtoSendFriendRequestCommand{FriendUserId: friendUserID, RequestMessage: message})
	if err != nil {
		return fmt.Errorf("encode friend request: %w", err)
	}
	return c.doJSON(ctx, http.MethodPost, "/api/im/friends/requests", accessToken, json.RawMessage(body), nil)
}

func (c *Client) ListIncomingFriendRequests(ctx context.Context, accessToken string) ([]types.FriendRequest, error) {
	return c.listFriendRequests(ctx, accessToken, "/api/im/friends/requests/incoming")
}

func (c *Client) ListOutgoingFriendRequests(ctx context.Context, accessToken string) ([]types.FriendRequest, error) {
	return c.listFriendRequests(ctx, accessToken, "/api/im/friends/requests/outgoing")
}

func (c *Client) AcceptFriendRequest(ctx context.Context, accessToken, friendUserID string) error {
	return c.handleFriendRequest(ctx, accessToken, "/api/im/friends/requests/accept", friendUserID)
}

func (c *Client) RejectFriendRequest(ctx context.Context, accessToken, friendUserID string) error {
	return c.handleFriendRequest(ctx, accessToken, "/api/im/friends/requests/reject", friendUserID)
}

func (c *Client) CancelFriendRequest(ctx context.Context, accessToken, friendUserID string) error {
	return c.handleFriendRequest(ctx, accessToken, "/api/im/friends/requests/cancel", friendUserID)
}

func (c *Client) listFriendRequests(ctx context.Context, accessToken, path string) ([]types.FriendRequest, error) {
	var response []json.RawMessage
	if err := c.doJSON(ctx, http.MethodGet, path, accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.FriendRequest, 0, len(response))
	for _, raw := range response {
		item := &pb.ProtoFriendRequest{}
		if err := protojson.Unmarshal(raw, item); err != nil {
			return nil, fmt.Errorf("decode friend request: %w", err)
		}
		items = append(items, types.FriendRequest{
			FromUserID: item.GetFromUserId(), ToUserID: item.GetToUserId(), RequestMessage: item.GetReqMsg(),
			Status: types.FriendRequestStatus(item.GetHandleResult()), HandleMessage: item.GetHandleMsg(),
			HandlerUserID: item.GetHandlerUserId(), HandleTime: item.GetHandleTime(), Extra: item.GetEx(),
			CreateTime: item.GetCreateTime(), UpdatedAt: item.GetUpdatedAt(),
		})
	}
	return items, nil
}

func (c *Client) handleFriendRequest(ctx context.Context, accessToken, path, friendUserID string) error {
	if strings.TrimSpace(friendUserID) == "" {
		return fmt.Errorf("friendUserID required")
	}
	body, err := protojson.Marshal(&pb.ProtoHandleFriendRequestCommand{FriendUserId: friendUserID})
	if err != nil {
		return fmt.Errorf("encode friend request action: %w", err)
	}
	return c.doJSON(ctx, http.MethodPost, path, accessToken, json.RawMessage(body), nil)
}

func (c *Client) doJSON(ctx context.Context, method, path, accessToken string, requestBody any, responseBody any) error {
	var bodyReader *bytes.Reader
	if requestBody == nil {
		bodyReader = bytes.NewReader(nil)
	} else {
		payload, err := json.Marshal(requestBody)
		if err != nil {
			return fmt.Errorf("marshal request: %w", err)
		}
		bodyReader = bytes.NewReader(payload)
	}
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, bodyReader)
	if err != nil {
		return fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if accessToken != "" {
		req.Header.Set("Authorization", "Bearer "+accessToken)
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("http request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode >= http.StatusBadRequest {
		return fmt.Errorf("unexpected status: %d", resp.StatusCode)
	}
	if responseBody == nil {
		return nil
	}
	if err := json.NewDecoder(resp.Body).Decode(responseBody); err != nil {
		return fmt.Errorf("decode response: %w", err)
	}
	return nil
}

func parseConversationKind(kind string) types.ConversationKind {
	switch strings.ToLower(kind) {
	case "group":
		return types.ConversationKindGroup
	default:
		return types.ConversationKindDirect
	}
}

func toConversation(item *pb.ProtoConversation) types.Conversation {
	return types.Conversation{
		OwnerUserID:        item.GetOwnerUserId(),
		ConversationID:     item.GetConversationId(),
		ConversationType:   int(item.GetConversationType()),
		TargetID:           item.GetTargetId(),
		ReceiveOpt:         int(item.GetReceiveOpt()),
		UnreadCount:        int(item.GetUnreadCount()),
		Pinned:             item.GetPinned(),
		AttachedInfo:       item.GetAttachedInfo(),
		GroupAtType:        int(item.GetGroupAtType()),
		AutoCleanup:        item.GetAutoCleanup(),
		CleanupCycle:       item.GetCleanupCycle(),
		LatestCleanupTime:  item.GetLatestCleanupTime(),
		CreatedAt:          item.GetCreatedAt(),
		UpdatedAt:          item.GetUpdatedAt(),
		Kind:               parseConversationKind(item.GetKind()),
		Title:              item.GetTitle(),
		Subtitle:           item.GetSubtitle(),
		LastMessagePreview: item.GetLastMessagePreview(),
		LastMessageTime:    item.GetLastMessageTime(),
		Notification:       item.GetNotification(),
	}
}
