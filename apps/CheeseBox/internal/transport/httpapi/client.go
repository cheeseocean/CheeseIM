package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/cheeseim/cheesebox/internal/domain"
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

func (c *Client) IssueWsTicket(ctx context.Context, accessToken, deviceID, platform string) (domain.WsTicket, error) {
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
		return domain.WsTicket{}, err
	}
	return domain.WsTicket{
		Ticket:   response.Ticket,
		ExpireAt: response.ExpireAt,
		WSURL:    response.WSURL,
	}, nil
}

func (c *Client) ListFriends(ctx context.Context, accessToken string) ([]domain.FriendSummary, error) {
	var response []domain.FriendSummary
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/friends", accessToken, nil, &response); err != nil {
		return nil, err
	}
	return response, nil
}

func (c *Client) ListConversations(ctx context.Context, accessToken string) ([]domain.ConversationSummary, error) {
	var response []struct {
		ConversationID     string `json:"conversationId"`
		Title              string `json:"title"`
		Subtitle           string `json:"subtitle"`
		Kind               string `json:"kind"`
		LastMessagePreview string `json:"lastMessagePreview"`
		LastMessageTime    int64  `json:"lastMessageTime"`
		UnreadCount        int    `json:"unreadCount"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/conversations", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]domain.ConversationSummary, 0, len(response))
	for _, item := range response {
		items = append(items, domain.ConversationSummary{
			ConversationID:     item.ConversationID,
			Title:              item.Title,
			Subtitle:           item.Subtitle,
			Kind:               parseConversationKind(item.Kind),
			LastMessagePreview: item.LastMessagePreview,
			LastMessageTime:    item.LastMessageTime,
			UnreadCount:        item.UnreadCount,
		})
	}
	return items, nil
}

func (c *Client) ListGroups(ctx context.Context, accessToken string) ([]domain.GroupSummary, error) {
	var response []struct {
		GroupID   string `json:"groupId"`
		GroupName string `json:"groupName"`
		FaceURL   string `json:"faceUrl"`
	}
	if err := c.doJSON(ctx, http.MethodGet, "/api/im/groups", accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]domain.GroupSummary, 0, len(response))
	for _, item := range response {
		items = append(items, domain.GroupSummary(item))
	}
	return items, nil
}

func (c *Client) LoadHistoryPage(ctx context.Context, accessToken, conversationID string, limit int) ([]domain.HistoryMessage, error) {
	path := fmt.Sprintf("/api/im/conversations/%s/messages?limit=%d", conversationID, limit)
	var response []struct {
		Sequence    int64  `json:"sequence"`
		ServerMsgID string `json:"serverMsgId"`
		SenderID    string `json:"senderId"`
		SenderName  string `json:"senderName"`
		Content     string `json:"content"`
		SendTime    int64  `json:"sendTime"`
	}
	if err := c.doJSON(ctx, http.MethodGet, path, accessToken, nil, &response); err != nil {
		return nil, err
	}
	items := make([]domain.HistoryMessage, 0, len(response))
	for _, item := range response {
		items = append(items, domain.HistoryMessage(item))
	}
	return items, nil
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

func parseConversationKind(kind string) domain.ConversationKind {
	switch strings.ToLower(kind) {
	case "group":
		return domain.ConversationKindGroup
	default:
		return domain.ConversationKindDirect
	}
}
