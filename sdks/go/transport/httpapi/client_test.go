package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"sort"
	"testing"
	"time"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestClientIssueWsTicket(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if got := r.Header.Get("Authorization"); got != "Bearer token-1" {
			t.Fatalf("Authorization = %q, want Bearer token-1", got)
		}
		var body map[string]string
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		if body["device_id"] != "device-1" || body["platform"] != "desktop" {
			t.Fatalf("unexpected body = %#v", body)
		}
		return jsonResponse(map[string]any{
			"ticket":    "ticket-1",
			"expire_at": int64(123),
			"ws_url":    "/ws",
		}), nil
	})
	ticket, err := client.IssueWsTicket(context.Background(), "token-1", "device-1", "desktop")
	if err != nil {
		t.Fatalf("IssueWsTicket() error = %v", err)
	}
	if ticket.Ticket != "ticket-1" || ticket.WSURL != "/ws" {
		t.Fatalf("unexpected ticket = %#v", ticket)
	}
}

func TestClientLogin(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.URL.Path != "/api/auth/login" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "" {
			t.Fatalf("Authorization = %q, want empty", got)
		}
		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		if body["userId"] != "user-1" || int(body["platformId"].(float64)) != 1 || body["deviceId"] != "device-1" {
			t.Fatalf("unexpected body = %#v", body)
		}
		return jsonResponse(map[string]any{
			"accessToken": "token-1",
		}), nil
	})
	token, err := client.Login(context.Background(), "user-1", "password", 1, "device-1", "CheeseBox/dev")
	if err != nil {
		t.Fatalf("Login() error = %v", err)
	}
	if token != "token-1" {
		t.Fatalf("token = %q, want token-1", token)
	}
}

func TestClientListFriends(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.URL.Path != "/api/im/friends" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		return jsonResponse([]map[string]any{{
			"friendId": "user-1",
			"remark":   "Alice",
		}}), nil
	})
	items, err := client.ListFriends(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("ListFriends() error = %v", err)
	}
	if len(items) != 1 || items[0].DisplayName != "Alice" || items[0].UserID != "user-1" {
		t.Fatalf("unexpected friends = %#v", items)
	}
}

func TestClientListGroups(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.URL.Path != "/api/im/groups" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		return jsonResponse([]map[string]any{{
			"groupId":   "group-1",
			"groupName": "Crew",
			"avatarUrl": "https://example.invalid/group.png",
		}}), nil
	})
	items, err := client.ListGroups(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("ListGroups() error = %v", err)
	}
	if len(items) != 1 || items[0].GroupName != "Crew" {
		t.Fatalf("unexpected groups = %#v", items)
	}
}

func TestClientListConversations(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.URL.Path != "/api/im/conversations" || r.Method != http.MethodGet {
			t.Fatalf("path = %q", r.URL.Path)
		}
		return jsonResponse([]map[string]any{{
			"ownerUserId":        "user-1",
			"conversationId":     "c1:user-1:user-2",
			"conversationType":   1,
			"targetId":           "user-2",
			"receiveOpt":         0,
			"pinned":             false,
			"attachedInfo":       "",
			"groupAtType":        0,
			"autoCleanup":        false,
			"cleanupCycle":       int64(0),
			"latestCleanupTime":  int64(0),
			"createdAt":          int64(111),
			"updatedAt":          int64(222),
			"title":              "Alice",
			"subtitle":           "Direct conversation",
			"kind":               "direct",
			"lastMessagePreview": "hello",
			"lastMessageTime":    int64(321),
			"unreadCount":        2,
			"notification":       false,
		}}), nil
	})
	items, err := client.ListConversations(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("ListConversations() error = %v", err)
	}
	if len(items) != 1 || items[0].Title != "Alice" || items[0].UnreadCount != 2 {
		t.Fatalf("unexpected conversations = %#v", items)
	}
}

func TestClientGetConversationMaxSeqs(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if got := r.URL.Path; got != "/api/im/conversations/max-seqs" {
			t.Fatalf("path = %q", got)
		}
		return jsonResponse([]map[string]any{{
			"conversationId": "s:user-1:user-2",
			"maxSeq":         int64(11),
		}}), nil
	})
	items, err := client.GetConversationMaxSeqs(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("GetConversationMaxSeqs() error = %v", err)
	}
	if len(items) != 1 || items[0].ConversationID != "s:user-1:user-2" || items[0].MaxSeq != 11 {
		t.Fatalf("unexpected max seqs = %#v", items)
	}
}

func TestClientGetConversationReadSnapshots(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if got := r.URL.Path; got != "/api/im/conversations/read-snapshots" {
			t.Fatalf("path = %q", got)
		}
		return jsonResponse([]map[string]any{{
			"conversationId": "s:user-1:user-2",
			"readSeq":        int64(8),
			"maxSeq":         int64(11),
			"unreadCount":    int64(3),
		}}), nil
	})
	items, err := client.GetConversationReadSnapshots(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("GetConversationReadSnapshots() error = %v", err)
	}
	if len(items) != 1 || items[0].ReadSeq != 8 || items[0].UnreadCount != 3 {
		t.Fatalf("unexpected read snapshots = %#v", items)
	}
}

func TestClientSyncConversations(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if got := r.URL.Path; got != "/api/im/conversations/sync/incremental" {
			t.Fatalf("path = %q", got)
		}
		if r.URL.Query().Get("versionId") != "v1" || r.URL.Query().Get("version") != "2" || r.URL.Query().Get("idHash") != "88" {
			t.Fatalf("query = %s", r.URL.RawQuery)
		}
		return jsonResponse(map[string]any{
			"versionId": "v1",
			"version":   int64(3),
			"idHash":    int64(99),
			"full":      false,
			"update": []map[string]any{{
				"conversationId": "s:user-1:user-2",
				"targetId":       "user-2",
				"title":          "Alice",
			}},
			"delete": []string{"s:user-1:user-3"},
		}), nil
	})

	result, err := client.SyncConversations(context.Background(), "token-1", types.ConversationSyncCursor{
		VersionID: "v1",
		Version:   2,
		IDHash:    88,
	})
	if err != nil {
		t.Fatalf("SyncConversations() error = %v", err)
	}
	if result.Version != 3 || result.IDHash != 99 || len(result.Update) != 1 || result.Update[0].Title != "Alice" {
		t.Fatalf("unexpected result = %#v", result)
	}
	if len(result.Delete) != 1 || result.Delete[0] != "s:user-1:user-3" {
		t.Fatalf("delete = %#v", result.Delete)
	}
}

func TestClientDeleteConversation(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Method != http.MethodDelete || r.URL.Path != "/api/im/conversations/s:user-1:user-2" {
			t.Fatalf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer token-1" {
			t.Fatalf("Authorization = %q, want Bearer token-1", got)
		}
		return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(bytes.NewReader(nil)), Header: make(http.Header)}, nil
	})

	if err := client.DeleteConversation(context.Background(), "token-1", "s:user-1:user-2"); err != nil {
		t.Fatalf("DeleteConversation() error = %v", err)
	}
}

func TestClientPullMessages(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Method != http.MethodPost || r.URL.Path != "/api/im/conversations/sync/pull" {
			t.Fatalf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		if int(body["limitPerConversation"].(float64)) != 50 {
			t.Fatalf("unexpected body = %#v", body)
		}
		ranges := body["ranges"].([]any)
		if len(ranges) != 1 {
			t.Fatalf("ranges = %#v", ranges)
		}
		return jsonResponse(map[string]any{
			"conversations": []map[string]any{{
				"conversationId": "s:user-1:user-2",
				"endSeq":         int64(11),
				"completed":      true,
				"messages": []map[string]any{{
					"seq":            int64(11),
					"serverMsgId":    "server-1",
					"senderId":       "user-1",
					"senderNickName": "Alice",
					"receiverId":     "user-2",
					"contentType":    int32(101),
					"sessionType":    int32(1),
					"content":        "aGVsbG8=",
					"sendTime":       int64(456),
				}},
			}},
		}), nil
	})
	items, err := client.PullMessages(context.Background(), "token-1", []types.SeqRange{{
		ConversationID: "s:user-1:user-2",
		BeginSeq:       1,
		EndSeq:         11,
	}}, 50)
	if err != nil {
		t.Fatalf("PullMessages() error = %v", err)
	}
	if len(items) != 1 || len(items[0].Messages) != 1 || string(items[0].Messages[0].Content) != "hello" {
		t.Fatalf("unexpected pull messages = %#v", items)
	}
}

func TestClientAckReadSeq(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Method != http.MethodPut || r.URL.Path != "/api/im/conversations/s:user-1:user-2/read-seq" {
			t.Fatalf("unexpected request %s %s", r.Method, r.URL.Path)
		}
		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		if int64(body["readSeq"].(float64)) != 12 {
			t.Fatalf("unexpected body = %#v", body)
		}
		return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(bytes.NewReader(nil)), Header: make(http.Header)}, nil
	})
	if err := client.AckReadSeq(context.Background(), "token-1", "s:user-1:user-2", 12); err != nil {
		t.Fatalf("AckReadSeq() error = %v", err)
	}
}

func TestClientAddFriend(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if r.Method != http.MethodPost {
			t.Fatalf("method = %q, want POST", r.Method)
		}
		if r.URL.Path != "/api/im/friends/requests" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		var body map[string]string
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		if body["friendUserId"] != "user-2" || body["requestMessage"] != "hi" {
			t.Fatalf("unexpected body = %#v", body)
		}
		return &http.Response{StatusCode: http.StatusOK, Body: io.NopCloser(bytes.NewReader(nil)), Header: make(http.Header)}, nil
	})
	if err := client.AddFriend(context.Background(), "token-1", "user-2", "hi"); err != nil {
		t.Fatalf("AddFriend() error = %v", err)
	}
}

func TestClientPullMessagesOrdersBodyRanges(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		var body struct {
			Ranges []struct {
				ConversationID string `json:"conversationId"`
			} `json:"ranges"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("Decode() error = %v", err)
		}
		got := []string{body.Ranges[0].ConversationID, body.Ranges[1].ConversationID}
		sort.Strings(got)
		if got[0] != "g:group-1" || got[1] != "s:user-1:user-2" {
			t.Fatalf("unexpected ranges = %#v", got)
		}
		return jsonResponse(map[string]any{"conversations": []any{}}), nil
	})
	_, err := client.PullMessages(context.Background(), "token-1", []types.SeqRange{
		{ConversationID: "g:group-1", BeginSeq: 1, EndSeq: 2},
		{ConversationID: "s:user-1:user-2", BeginSeq: 3, EndSeq: 4},
	}, 20)
	if err != nil {
		t.Fatalf("PullMessages() error = %v", err)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (fn roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) {
	return fn(r)
}

func jsonResponse(body any) *http.Response {
	payload, err := json.Marshal(body)
	if err != nil {
		panic(err)
	}
	return &http.Response{
		StatusCode: http.StatusOK,
		Body:       io.NopCloser(bytes.NewReader(payload)),
		Header:     make(http.Header),
	}
}
