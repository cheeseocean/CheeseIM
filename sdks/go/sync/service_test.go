package imsync

import (
	"context"
	"testing"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestServiceOpenConversationPullsTailOnFirstOpen(t *testing.T) {
	api := &fakePullAPI{
		pulled: []types.PulledConversationMessages{{
			ConversationID: "s:u100:u200",
			EndSeq:         12,
			Completed:      true,
			Messages: []types.Message{
				{Sequence: 11, ServerMsgID: "m11"},
				{Sequence: 12, ServerMsgID: "m12"},
			},
		}},
	}
	service := NewService(api, "u100")
	service.Bootstrap(types.BootstrapData{
		MaxSeqs: map[string]int64{"s:u100:u200": 12},
	})

	items, err := service.OpenConversation(context.Background(), "token-1", "s:u100:u200", 2)
	if err != nil {
		t.Fatalf("OpenConversation() error = %v", err)
	}
	// 本地消息为空时，从 beginSeq=1 拉取到 serverMax=12，返回拉取的消息
	if len(items) != 2 || items[0].Sequence != 11 || api.lastRange.BeginSeq != 1 {
		t.Fatalf("items = %#v, range = %#v", items, api.lastRange)
	}
}

func TestServiceHandleRealtimeRepairsGap(t *testing.T) {
	api := &fakePullAPI{
		pulled: []types.PulledConversationMessages{{
			ConversationID: "s:u100:u200",
			EndSeq:         12,
			Completed:      true,
			Messages: []types.Message{
				{Sequence: 11, ServerMsgID: "m11", SenderID: "u200", ReceiverID: "u100", ChatType: 1},
				{Sequence: 12, ServerMsgID: "m12", SenderID: "u200", ReceiverID: "u100", ChatType: 1},
			},
		}},
	}
	service := NewService(api, "u100")
	service.Bootstrap(types.BootstrapData{
		MaxSeqs: map[string]int64{"s:u100:u200": 12},
	})
	service.messagesByConv["s:u100:u200"] = []types.Message{{Sequence: 10, ServerMsgID: "m10"}}

	conversationID, items, repaired, err := service.HandleRealtimeMessage(context.Background(), "token-1", types.Message{
		Sequence:    12,
		ServerMsgID: "m12",
		SenderID:    "u200",
		ReceiverID:  "u100",
		ChatType:    1,
	})
	if err != nil {
		t.Fatalf("HandleRealtimeMessage() error = %v", err)
	}
	// 修复间隙后，返回拉取的消息（不含本地已缓存的消息 10）
	// 本地已缓存的消息仍然存储在 messagesByConv 中，但不再返回
	if conversationID != "s:u100:u200" || !repaired || len(items) != 2 || items[0].Sequence != 11 {
		t.Fatalf("conversationID=%q repaired=%v items=%#v", conversationID, repaired, items)
	}
}

func TestServiceMarkReadUpdatesSnapshot(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api, "u100")
	service.Bootstrap(types.BootstrapData{
		ReadSnapshots: map[string]types.ReadSnapshot{
			"s:u100:u200": {ConversationID: "s:u100:u200", ReadSeq: 5, MaxSeq: 10, UnreadCount: 5},
		},
	})

	snapshot, err := service.MarkRead(context.Background(), "token-1", "s:u100:u200", 10)
	if err != nil {
		t.Fatalf("MarkRead() error = %v", err)
	}
	if snapshot.UnreadCount != 0 || api.ackedConversationID != "s:u100:u200" || api.ackedReadSeq != 10 {
		t.Fatalf("snapshot=%#v api=%#v", snapshot, api)
	}
}

type fakePullAPI struct {
	pulled             []types.PulledConversationMessages
	lastRange          types.SeqRange
	ackedConversationID string
	ackedReadSeq       int64
}

func (f *fakePullAPI) PullMessages(_ context.Context, _ string, ranges []types.SeqRange, _ int) ([]types.PulledConversationMessages, error) {
	if len(ranges) > 0 {
		f.lastRange = ranges[0]
	}
	return f.pulled, nil
}

func (f *fakePullAPI) AckReadSeq(_ context.Context, _ string, conversationID string, readSeq int64) error {
	f.ackedConversationID = conversationID
	f.ackedReadSeq = readSeq
	return nil
}
