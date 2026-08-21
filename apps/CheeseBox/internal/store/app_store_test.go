package store

import (
	"testing"

	"github.com/cheeseim/cheesebox/internal/domain"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestAppStoreUpsertConversationOrdersByLastMessageTime(t *testing.T) {
	store := New()
	store.UpsertConversation(domain.ConversationSummary{ConversationID: "c1", LastMessageTime: 1})
	store.UpsertConversation(domain.ConversationSummary{ConversationID: "c2", LastMessageTime: 2})

	if len(store.ConversationOrder) != 2 || store.ConversationOrder[0] != "c2" {
		t.Fatalf("unexpected order = %#v", store.ConversationOrder)
	}
}

func TestAppStoreAppendMessageAndToast(t *testing.T) {
	store := New()
	store.SetFriends([]domain.FriendSummary{{UserID: "user-1"}})
	store.SetGroups([]domain.GroupSummary{{GroupID: "group-1"}})
	store.AppendMessage("c1", domain.MessageItem{ID: "m1"})
	store.PushToast(domain.ToastKindError, "boom")

	if len(store.Friends) != 1 || len(store.Groups) != 1 {
		t.Fatalf("roster = %#v %#v", store.Friends, store.Groups)
	}
	if len(store.MessagesByConv["c1"]) != 1 {
		t.Fatalf("messages = %#v", store.MessagesByConv)
	}
	if store.Toast.Message != "boom" || store.Toast.Kind != domain.ToastKindError {
		t.Fatalf("toast = %#v", store.Toast)
	}
}

func TestAppStoreAppendMessageDeduplicatesByStableMessageIdentity(t *testing.T) {
	store := New()

	store.AppendMessage("c1", domain.MessageItem{ID: "server-1", ServerMsgID: "server-1", Sequence: 7, Content: "hello"})
	store.AppendMessage("c1", domain.MessageItem{ID: "server-1", ServerMsgID: "server-1", Sequence: 7, Content: "hello again"})
	store.AppendMessage("c1", domain.MessageItem{ID: "server-2", ServerMsgID: "server-2", Sequence: 8, Content: "next"})

	items := store.MessagesByConv["c1"]
	if len(items) != 2 {
		t.Fatalf("messages = %#v, want 2 deduped items", items)
	}
	if items[0].Content != "hello" {
		t.Fatalf("first message was overwritten: %#v", items[0])
	}
}

func TestAppStoreAdvancesOutgoingDeliveryState(t *testing.T) {
	store := New()
	store.AppendMessage("s:u1:u2", domain.MessageItem{
		ClientMsgID: "client-1", Self: true, DeliveryState: string(sdktypes.MessageDeliverySending),
	})

	store.UpdateSendAck("client-1", "server-1")
	item := store.MessagesByConv["s:u1:u2"][0]
	if item.ServerMsgID != "server-1" || item.DeliveryState != string(sdktypes.MessageDeliveryBrokerAccepted) {
		t.Fatalf("item = %#v, want broker accepted", item)
	}

	store.SetMessages("s:u1:u2", []domain.MessageItem{
		{ClientMsgID: "client-1", ServerMsgID: "server-1", Sequence: 7, Self: true},
	})
	store.UpdateDeliveredThrough("s:u1:u2", 7)
	item = store.MessagesByConv["s:u1:u2"][0]
	if item.DeliveryState != string(sdktypes.MessageDeliveryDelivered) {
		t.Fatalf("DeliveryState = %q, want delivered", item.DeliveryState)
	}
}

func TestAppStoreAppliesDeliveryHighWatermarkToLateMessage(t *testing.T) {
	store := New()
	store.UpdateDeliveredThrough("s:u1:u2", 9)
	store.SetMessages("s:u1:u2", []domain.MessageItem{
		{ServerMsgID: "server-1", Sequence: 9, Self: true},
	})

	item := store.MessagesByConv["s:u1:u2"][0]
	if item.DeliveryState != string(sdktypes.MessageDeliveryDelivered) {
		t.Fatalf("DeliveryState = %q, want delivered", item.DeliveryState)
	}
}
