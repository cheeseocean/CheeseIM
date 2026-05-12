package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/cheeseim/cheeseim-go-sdk/types"
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

func (c *Client) Login(ctx context.Context, userID, password string, platformID int, deviceID, clientVersion string) (string, error) {
	body := map[string]any{
		"userId":        userID,
		"platformId":    platformID,
		"deviceId":      deviceID,
		"clientVersion": clientVersion,
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
	var response []struct {
		UserID      string `json:"friendId"`
		DisplayName string `json:"remark"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/friends", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Friend, 0, len(response))
	for _, item := range response {
		items = append(items, types.Friend{
			UserID:      item.UserID,
			DisplayName: item.DisplayName,
		})
	}
	return items, nil
}

func (c *Client) ListConversations(ctx context.Context, accessToken string) ([]types.Conversation, error) {
	var response []struct {
		OwnerUserID        string `json:"ownerUserId"`
		ConversationID     string `json:"conversationId"`
		ConversationType   int    `json:"conversationType"`
		TargetID           string `json:"targetId"`
		ReceiveOpt         int    `json:"receiveOpt"`
		UnreadCount        int    `json:"unreadCount"`
		Pinned             bool   `json:"pinned"`
		AttachedInfo       string `json:"attachedInfo"`
		GroupAtType        int    `json:"groupAtType"`
		AutoCleanup        bool   `json:"autoCleanup"`
		CleanupCycle       int64  `json:"cleanupCycle"`
		LatestCleanupTime  int64  `json:"latestCleanupTime"`
		CreatedAt          int64  `json:"createdAt"`
		UpdatedAt          int64  `json:"updatedAt"`
		Kind               string `json:"kind"`
		Title              string `json:"title"`
		Subtitle           string `json:"subtitle"`
		LastMessagePreview string `json:"lastMessagePreview"`
		LastMessageTime    int64  `json:"lastMessageTime"`
		Notification       bool   `json:"notification"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/conversations", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Conversation, 0, len(response))
	for _, item := range response {
		items = append(items, types.Conversation{
			OwnerUserID:        item.OwnerUserID,
			ConversationID:     item.ConversationID,
			ConversationType:   item.ConversationType,
			TargetID:           item.TargetID,
			ReceiveOpt:         item.ReceiveOpt,
			UnreadCount:        item.UnreadCount,
			Pinned:             item.Pinned,
			AttachedInfo:       item.AttachedInfo,
			GroupAtType:        item.GroupAtType,
			AutoCleanup:        item.AutoCleanup,
			CleanupCycle:       item.CleanupCycle,
			LatestCleanupTime:  item.LatestCleanupTime,
			CreatedAt:          item.CreatedAt,
			UpdatedAt:          item.UpdatedAt,
			Title:              item.Title,
			Subtitle:           item.Subtitle,
			Kind:               parseConversationKind(item.Kind),
			LastMessagePreview: item.LastMessagePreview,
			LastMessageTime:    item.LastMessageTime,
			Notification:       item.Notification,
		})
	}
	return items, nil
}

func (c *Client) ListGroups(ctx context.Context, accessToken string) ([]types.Group, error) {
	var response []struct {
		GroupID   string `json:"groupId"`
		GroupName string `json:"groupName"`
		AvatarURL string `json:"avatarUrl"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/groups", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]types.Group, 0, len(response))
	for _, item := range response {
		items = append(items, types.Group(item))
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

func (c *Client) PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limitPerConversation int) ([]types.PulledConversationMessages, error) {
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
	body := map[string]string{
		"friendUserId":   friendUserID,
		"requestMessage": message,
	}
	return c.doJSON(ctx, http.MethodPost, "/api/im/friends/requests", accessToken, body, nil)
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
