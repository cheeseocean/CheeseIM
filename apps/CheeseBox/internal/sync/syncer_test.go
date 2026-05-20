package sync

import (
	"context"
	"testing"

	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestSyncerHandleRealtimeReturnsMergedMessagesAfterGapRepair(t *testing.T) {
	store := NewMemoryStore()
	store.SetMessages("s:user-1:user-2", []sdktypes.Message{
		{Sequence: 1, SenderID: "user-1", ReceiverID: "user-2", ChatType: 1, Content: []byte("one")},
	})
	puller := &fakePuller{
		pulled: []sdktypes.PulledConversationMessages{
			{
				ConversationID: "s:user-1:user-2",
				Messages: []sdktypes.Message{
					{Sequence: 2, SenderID: "user-2", ReceiverID: "user-1", ChatType: 1, Content: []byte("two")},
				},
			},
		},
	}
	syncer := NewSyncer(store, puller, func(string) int64 { return 1 }, func(string, int64) {})

	result, err := syncer.HandleRealtime(context.Background(), sdktypes.Message{
		Sequence:   3,
		SenderID:   "user-2",
		ReceiverID: "user-1",
		ChatType:   1,
		Content:    []byte("three"),
	})
	if err != nil {
		t.Fatalf("HandleRealtime() error = %v", err)
	}

	if !result.Repaired {
		t.Fatal("HandleRealtime() Repaired = false, want true")
	}
	if result.ConversationID != "s:user-1:user-2" {
		t.Fatalf("ConversationID = %q", result.ConversationID)
	}
	if len(result.Messages) != 3 {
		t.Fatalf("messages = %#v", result.Messages)
	}
	for i, want := range []int64{1, 2, 3} {
		if result.Messages[i].Sequence != want {
			t.Fatalf("messages[%d].Sequence = %d, want %d", i, result.Messages[i].Sequence, want)
		}
	}
}

type fakePuller struct {
	pulled []sdktypes.PulledConversationMessages
}

func (f *fakePuller) PullMessages(context.Context, []sdktypes.SeqRange, int64) ([]sdktypes.PulledConversationMessages, error) {
	return f.pulled, nil
}

func (f *fakePuller) GetSyncedMaxSeq(string) int64 {
	return 0
}
