package client

import (
	"context"
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

func TestClientEmitsReadUpdatedOnMarkRead(t *testing.T) {
	client := &Client{
		events:      make(chan types.Event, 1),
		accessToken: "token-1",
		sync:        imsyncForTest(),
	}
	_, err := client.MarkRead(context.Background(), "s:u100:u200", 10)
	if err != nil {
		t.Fatalf("MarkRead() error = %v", err)
	}
	event := <-client.Events()
	if event.Kind != types.EventKindReadUpdated || event.ReadSnapshot == nil || event.ReadSnapshot.ReadSeq != 10 {
		t.Fatalf("event = %#v", event)
	}
}

func imsyncForTest() *testSyncService {
	return &testSyncService{}
}

type testSyncService struct{}

func (t *testSyncService) Bootstrap(types.BootstrapData) {}

func (t *testSyncService) Reset(string) {}

func (t *testSyncService) OpenConversation(context.Context, string, string, int) ([]types.Message, error) {
	return nil, nil
}

func (t *testSyncService) HandleRealtimeMessage(context.Context, string, types.Message) (string, []types.Message, bool, error) {
	return "", nil, false, nil
}

func (t *testSyncService) MarkRead(context.Context, string, string, int64) (types.ReadSnapshot, error) {
	return types.ReadSnapshot{ConversationID: "s:u100:u200", ReadSeq: 10}, nil
}
