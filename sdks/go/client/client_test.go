package client

import (
	"testing"
	"time"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestClientCurrentUserID(t *testing.T) {
	client := New(Config{
		APIBaseURL: "https://example.invalid",
		TCPAddr:    "127.0.0.1:9000",
		DeviceID:   "device-1",
		Platform:   "desktop",
		Timeout:    time.Second,
	})
	if client.CurrentUserID() != "" {
		t.Fatalf("CurrentUserID() = %q, want empty", client.CurrentUserID())
	}
}

func TestResolveChatTarget(t *testing.T) {
	receiverID, groupID, sessionType, err := resolveChatTarget("s:u100:u200", "u100")
	if err != nil {
		t.Fatalf("resolveChatTarget() error = %v", err)
	}
	if receiverID != "u200" || groupID != "" || sessionType != 1 {
		t.Fatalf("unexpected target = %q %q %d", receiverID, groupID, sessionType)
	}
}

func TestResolveChatTargetGroup(t *testing.T) {
	receiverID, groupID, sessionType, err := resolveChatTarget("g:g123", "u100")
	if err != nil {
		t.Fatalf("resolveChatTarget() error = %v", err)
	}
	if receiverID != "" || groupID != "g123" || sessionType != 2 {
		t.Fatalf("unexpected target = %q %q %d", receiverID, groupID, sessionType)
	}
}

func TestResolveConversationID(t *testing.T) {
	tests := []struct {
		name     string
		message  types.Message
		expected string
	}{
		{
			name: "private chat sender < receiver",
			message: types.Message{
				SenderID: "a", ReceiverID: "b", ChatType: 1,
			},
			expected: "s:a:b",
		},
		{
			name: "private chat sender > receiver",
			message: types.Message{
				SenderID: "z", ReceiverID: "a", ChatType: 1,
			},
			expected: "s:a:z",
		},
		{
			name: "group chat",
			message: types.Message{
				GroupID: "g123", ChatType: 2,
			},
			expected: "g:g123",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := resolveConversationID(tt.message)
			if result != tt.expected {
				t.Errorf("resolveConversationID() = %q, want %q", result, tt.expected)
			}
		})
	}
}
