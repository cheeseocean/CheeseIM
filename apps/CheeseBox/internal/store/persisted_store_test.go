package store

import (
	"path/filepath"
	"reflect"
	"testing"
)

func TestPersistedStoreDeliveryAckSurvivesReloadAndCompletes(t *testing.T) {
	dir := t.TempDir()
	first, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("NewPersistedStore() error = %v", err)
	}
	first.AppendMessage("s:u1:u2", MessageRecord{ServerMsgID: "m1", Sequence: 7})
	if err := first.StageDeliveryAck(PendingDeliveryAck{OperationID: "op-1", ConversationID: "s:u1:u2", DeliveredSeq: 7}); err != nil {
		t.Fatalf("StageDeliveryAck() error = %v", err)
	}
	reloaded, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("reload error = %v", err)
	}
	want := []PendingDeliveryAck{{OperationID: "op-1", ConversationID: "s:u1:u2", DeliveredSeq: 7}}
	if got := reloaded.PendingDeliveryAcks(); !reflect.DeepEqual(got, want) {
		t.Fatalf("PendingDeliveryAcks() = %#v, want %#v", got, want)
	}
	if len(reloaded.GetMessages("s:u1:u2")) != 1 {
		t.Fatal("message and pending ack were not persisted together")
	}
	if err := reloaded.CompleteDeliveryAck("op-1"); err != nil {
		t.Fatalf("CompleteDeliveryAck() error = %v", err)
	}
	again, err := NewPersistedStore(dir)
	if err != nil || len(again.PendingDeliveryAcks()) != 0 {
		t.Fatalf("completed ack survived reload: %#v, err=%v", again.PendingDeliveryAcks(), err)
	}
}

func TestPersistedStoreCoalescesDeliveryAckByConversation(t *testing.T) {
	persisted, err := NewPersistedStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	_ = persisted.StageDeliveryAck(PendingDeliveryAck{OperationID: "op-1", ConversationID: "g:1", DeliveredSeq: 7})
	_ = persisted.StageDeliveryAck(PendingDeliveryAck{OperationID: "op-2", ConversationID: "g:1", DeliveredSeq: 9})
	got := persisted.PendingDeliveryAcks()
	if len(got) != 1 || got[0].OperationID != "op-2" || got[0].DeliveredSeq != 9 {
		t.Fatalf("PendingDeliveryAcks() = %#v", got)
	}
}

func TestNewPersistedStoreForUserUsesSeparateNamespacePerUser(t *testing.T) {
	baseDir := t.TempDir()

	first, err := NewPersistedStoreForUser(baseDir, "user-1")
	if err != nil {
		t.Fatalf("NewPersistedStoreForUser() error = %v", err)
	}
	second, err := NewPersistedStoreForUser(baseDir, "user/2")
	if err != nil {
		t.Fatalf("NewPersistedStoreForUser() error = %v", err)
	}

	if first.path() == second.path() {
		t.Fatalf("paths should differ, got %q", first.path())
	}
	if filepath.Dir(filepath.Dir(first.path())) != filepath.Join(baseDir, "users") {
		t.Fatalf("first path = %q, want under users namespace", first.path())
	}
}

func TestPersistedStoreControlEventCursorSurvivesReloadAndNeverRegresses(t *testing.T) {
	dir := t.TempDir()
	first, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("NewPersistedStore() error = %v", err)
	}
	if err := first.SetControlEventCursor(42); err != nil {
		t.Fatalf("SetControlEventCursor() error = %v", err)
	}
	if err := first.SetControlEventCursor(41); err != nil {
		t.Fatalf("regressing SetControlEventCursor() error = %v", err)
	}

	reloaded, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("reload NewPersistedStore() error = %v", err)
	}
	if got := reloaded.GetControlEventCursor(); got != 42 {
		t.Fatalf("control cursor = %d, want 42", got)
	}
}
