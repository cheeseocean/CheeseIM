package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"testing"
	"time"
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
			"userId":      "user-1",
			"displayName": "Alice",
			"avatarSeed":  "seed-1",
		}}), nil
	})
	items, err := client.ListFriends(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("ListFriends() error = %v", err)
	}
	if len(items) != 1 || items[0].DisplayName != "Alice" {
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
			"faceUrl":   "https://example.invalid/group.png",
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
		if r.URL.Path != "/api/im/conversations" {
			t.Fatalf("path = %q", r.URL.Path)
		}
		return jsonResponse([]map[string]any{{
			"conversationId":     "c1:user-1:user-2",
			"title":              "Alice",
			"subtitle":           "Direct conversation",
			"kind":               "direct",
			"lastMessagePreview": "hello",
			"lastMessageTime":    int64(321),
			"unreadCount":        2,
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

func TestClientLoadHistoryPage(t *testing.T) {
	client := New("https://example.invalid", time.Second)
	client.httpClient.Transport = roundTripFunc(func(r *http.Request) (*http.Response, error) {
		if got := r.URL.Path; got != "/api/im/conversations/c1:user-1:user-2/messages" {
			t.Fatalf("path = %q", got)
		}
		if got := r.URL.Query().Get("limit"); got != "50" {
			t.Fatalf("limit = %q, want 50", got)
		}
		return jsonResponse([]map[string]any{{
			"sequence":    int64(11),
			"serverMsgId": "server-1",
			"senderId":    "user-1",
			"senderName":  "Alice",
			"content":     "hello",
			"sendTime":    int64(456),
		}}), nil
	})
	items, err := client.LoadHistoryPage(context.Background(), "token-1", "c1:user-1:user-2", 50)
	if err != nil {
		t.Fatalf("LoadHistoryPage() error = %v", err)
	}
	if len(items) != 1 || items[0].SenderName != "Alice" || items[0].Sequence != 11 {
		t.Fatalf("unexpected history = %#v", items)
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
