package client

import (
	"context"
	"testing"
	"time"

	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
	imsync "github.com/cheeseim/cheeseim-go-sdk/sync"
	"github.com/cheeseim/cheeseim-go-sdk/transport/tcpim"
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

func TestClientDeleteConversationRequiresInitializedSession(t *testing.T) {
	client := New(Config{})

	if err := client.DeleteConversation(context.Background(), "s:user-1:user-2"); err == nil {
		t.Fatal("DeleteConversation() error = nil, want initialized session error")
	}
}

func TestClientForceLogoutClearsSessionAndEmitsTypedEvent(t *testing.T) {
	client := New(Config{})
	client.accessToken = "access-token"
	client.currentUser = "user-1"
	client.sync = imsync.NewService(client.social)
	client.sync.SetAccessToken("access-token")

	client.handleTransportEvent(tcpim.Event{
		Kind:      tcpim.EventForceLogout,
		RequestID: "force-1",
		ForceLogout: &pb.ProtoForceLogoutNotify{
			Reason: "session replaced", SessionId: "session-1", DeviceId: "device-1", OccurredAt: 123,
		},
	})

	if client.CurrentUserID() != "" || client.accessTokenSnapshot() != "" {
		t.Fatalf("session was not cleared: user=%q token=%q", client.CurrentUserID(), client.accessTokenSnapshot())
	}
	event := <-client.Events()
	if event.Kind != types.EventKindForcedLogout || event.ForceLogout == nil || event.ForceLogout.SessionID != "session-1" {
		t.Fatalf("event = %#v, want typed forced logout", event)
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
