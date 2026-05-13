package imsync

import (
	"context"
	"testing"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestNewService(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	if service == nil {
		t.Fatal("NewService() returned nil")
	}
	if service.api != api {
		t.Error("NewService() did not set api")
	}
}

func TestServiceSetAccessToken(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	service.SetAccessToken("token-123")
	service.mu.RLock()
	if service.accessToken != "token-123" {
		t.Errorf("SetAccessToken() token = %q, want token-123", service.accessToken)
	}
	service.mu.RUnlock()
}

func TestServiceReset(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	service.Bootstrap(types.BootstrapData{
		MaxSeqs: map[string]int64{"conv1": 10},
	})
	service.Reset()
	if seq := service.GetSyncedMaxSeq("conv1"); seq != 0 {
		t.Errorf("Reset() seq = %d, want 0", seq)
	}
}

func TestServiceBootstrap(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	service.Bootstrap(types.BootstrapData{
		MaxSeqs: map[string]int64{"conv1": 10, "conv2": 20},
	})
	if seq := service.GetSyncedMaxSeq("conv1"); seq != 10 {
		t.Errorf("Bootstrap() conv1 seq = %d, want 10", seq)
	}
	if seq := service.GetSyncedMaxSeq("conv2"); seq != 20 {
		t.Errorf("Bootstrap() conv2 seq = %d, want 20", seq)
	}
}

func TestServiceUpdateSyncedMaxSeq(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	service.UpdateSyncedMaxSeq("conv1", 10)
	if seq := service.GetSyncedMaxSeq("conv1"); seq != 10 {
		t.Errorf("UpdateSyncedMaxSeq() seq = %d, want 10", seq)
	}
	// 不应更新到更小的值
	service.UpdateSyncedMaxSeq("conv1", 5)
	if seq := service.GetSyncedMaxSeq("conv1"); seq != 10 {
		t.Errorf("UpdateSyncedMaxSeq() should not decrease seq = %d, want 10", seq)
	}
	// 应更新到更大的值
	service.UpdateSyncedMaxSeq("conv1", 15)
	if seq := service.GetSyncedMaxSeq("conv1"); seq != 15 {
		t.Errorf("UpdateSyncedMaxSeq() seq = %d, want 15", seq)
	}
}

func TestServicePullMessages(t *testing.T) {
	api := &fakePullAPI{
		pulled: []types.PulledConversationMessages{{
			ConversationID: "conv1",
			EndSeq:         5,
			Completed:      true,
			Messages: []types.Message{
				{Sequence: 1, ServerMsgID: "m1"},
				{Sequence: 5, ServerMsgID: "m5"},
			},
		}},
	}
	service := NewService(api)
	service.SetAccessToken("token-1")

	result, err := service.PullMessages(context.Background(), []types.SeqRange{{BeginSeq: 1, EndSeq: 5}}, 20)
	if err != nil {
		t.Fatalf("PullMessages() error = %v", err)
	}
	if len(result) != 1 {
		t.Fatalf("PullMessages() result count = %d, want 1", len(result))
	}
	if result[0].ConversationID != "conv1" {
		t.Errorf("PullMessages() convID = %q, want conv1", result[0].ConversationID)
	}
	if api.lastRange.BeginSeq != 1 || api.lastRange.EndSeq != 5 {
		t.Errorf("PullMessages() range = %#v, want {BeginSeq:1, EndSeq:5}", api.lastRange)
	}
}

func TestServiceMarkRead(t *testing.T) {
	api := &fakePullAPI{}
	service := NewService(api)
	service.SetAccessToken("token-1")

	err := service.MarkRead(context.Background(), "conv1", 10)
	if err != nil {
		t.Fatalf("MarkRead() error = %v", err)
	}
	if api.ackedConversationID != "conv1" {
		t.Errorf("MarkRead() ackedConversationID = %q, want conv1", api.ackedConversationID)
	}
	if api.ackedReadSeq != 10 {
		t.Errorf("MarkRead() ackedReadSeq = %d, want 10", api.ackedReadSeq)
	}
}

type fakePullAPI struct {
	pulled              []types.PulledConversationMessages
	lastRange           types.SeqRange
	ackedConversationID string
	ackedReadSeq        int64
}

func (f *fakePullAPI) PullMessages(_ context.Context, _ string, ranges []types.SeqRange, _ int64) ([]types.PulledConversationMessages, error) {
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
