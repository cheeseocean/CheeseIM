package service

import (
	"context"
	"testing"

	"github.com/cheeseim/cheesebox/internal/domain"
	pb "github.com/cheeseim/cheesebox/internal/proto"
	"github.com/cheeseim/cheesebox/internal/transport/tcpim"
)

func TestChatServiceSendText(t *testing.T) {
	sender := &fakeChatSender{}
	service := NewChatService(sender, &fakeRosterClient{})

	item, err := service.SendText("client-1", "s:user-1:user-2", "user-1", "hello")
	if err != nil {
		t.Fatalf("SendText() error = %v", err)
	}
	if item.Content != "hello" || !item.Self {
		t.Fatalf("unexpected item = %#v", item)
	}
	if sender.message == nil || string(sender.message.GetContent()) != "hello" || sender.message.GetReceiverId() != "user-2" || sender.message.GetContentType() != 101 {
		t.Fatalf("unexpected outbound message = %#v", sender.message)
	}
}

func TestChatServiceOpenConversation(t *testing.T) {
	history := []domain.HistoryMessage{{Sequence: 1, Content: "hello"}}
	service := NewChatService(&fakeChatSender{}, &fakeRosterClient{history: history})
	items, err := service.OpenConversation(context.Background(), "token-1", "s:user-1:user-2", 50)
	if err != nil {
		t.Fatalf("OpenConversation() error = %v", err)
	}
	if len(items) != 1 || items[0].Content != "hello" {
		t.Fatalf("unexpected items = %#v", items)
	}
}

func TestChatServiceSendTextLegacyConversationID(t *testing.T) {
	sender := &fakeChatSender{}
	service := NewChatService(sender, &fakeRosterClient{})

	if _, err := service.SendText("client-2", "c1:user-1:user-2", "user-1", "hello"); err != nil {
		t.Fatalf("SendText() error = %v", err)
	}
	if sender.message == nil || sender.message.GetReceiverId() != "user-2" {
		t.Fatalf("unexpected outbound message = %#v", sender.message)
	}
}

func TestChatServiceHandleRealtimeEvent(t *testing.T) {
	service := NewChatService(&fakeChatSender{}, &fakeRosterClient{})
	item, ok := service.HandleRealtimeEvent(tcpim.Event{
		Kind: tcpim.EventMessage,
		Message: &pb.ProtoMessage{
			ServerMsgId: "server-1",
			SenderId:    "user-2",
			Content:     []byte("hello"),
		},
	})
	if !ok || item.Content != "hello" || item.Self {
		t.Fatalf("unexpected result = (%#v, %v)", item, ok)
	}
}

func TestChatServiceResolveRealtimeEvent(t *testing.T) {
	service := NewChatService(&fakeChatSender{}, &fakeRosterClient{})
	conversationID, item, ok := service.ResolveRealtimeEvent(tcpim.Event{
		Kind: tcpim.EventMessage,
		Message: &pb.ProtoMessage{
			ServerMsgId: "server-1",
			SenderId:    "user-2",
			ReceiverId:  "user-1",
			SessionType: 1,
			Content:     []byte("hello"),
		},
	}, "user-1")
	if !ok {
		t.Fatal("ResolveRealtimeEvent() = false, want true")
	}
	if conversationID != "s:user-1:user-2" {
		t.Fatalf("conversationID = %q, want s:user-1:user-2", conversationID)
	}
	if item.Content != "hello" || item.Self {
		t.Fatalf("unexpected item = %#v", item)
	}
}

type fakeChatSender struct {
	requestID string
	message   *pb.ProtoMessage
	err       error
}

func (f *fakeChatSender) SendChatMessage(requestID string, message *pb.ProtoMessage) error {
	f.requestID = requestID
	f.message = message
	return f.err
}
