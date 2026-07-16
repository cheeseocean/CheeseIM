package proto

import (
	"testing"

	"google.golang.org/protobuf/proto"
)

func TestDeliveryAckEnvelopeRoundTrip(t *testing.T) {
	want := &ProtoClientEnvelope{
		Command:   37,
		RequestId: "delivery-op-1",
		Payload: &ProtoClientEnvelope_ChatDeliveryAck{
			ChatDeliveryAck: &ProtoChatDeliveryAckCommand{
				ConversationId:  "s:u1:u2",
				MaxDeliveredSeq: 42,
				DeviceId:        "device-1",
				OpId:            "delivery-op-1",
			},
		},
	}

	encoded, err := proto.Marshal(want)
	if err != nil {
		t.Fatalf("marshal delivery ack envelope: %v", err)
	}
	got := new(ProtoClientEnvelope)
	if err := proto.Unmarshal(encoded, got); err != nil {
		t.Fatalf("unmarshal delivery ack envelope: %v", err)
	}
	ack := got.GetChatDeliveryAck()
	if ack == nil || ack.GetConversationId() != "s:u1:u2" || ack.GetMaxDeliveredSeq() != 42 ||
		ack.GetDeviceId() != "device-1" || ack.GetOpId() != "delivery-op-1" {
		t.Fatalf("delivery ack oneof mismatch: %#v", ack)
	}
}

func TestServerAckAndDeliveryNotifyEnvelopeRoundTrip(t *testing.T) {
	tests := []struct {
		name     string
		envelope *ProtoServerEnvelope
		verify   func(*testing.T, *ProtoServerEnvelope)
	}{
		{
			name: "broker accepted ack",
			envelope: &ProtoServerEnvelope{Command: 31, RequestId: "send-1",
				Payload: &ProtoServerEnvelope_ChatSendAck{ChatSendAck: &ProtoChatSendAck{
					ServerMsgId: "server-1", ClientMsgId: "client-1", AcceptedAt: 123456,
					AcceptedState: ProtoChatSendAcceptedState_CHAT_SEND_BROKER_ACCEPTED,
				}}},
			verify: func(t *testing.T, got *ProtoServerEnvelope) {
				ack := got.GetChatSendAck()
				if ack == nil || ack.GetAcceptedAt() != 123456 ||
					ack.GetAcceptedState() != ProtoChatSendAcceptedState_CHAT_SEND_BROKER_ACCEPTED {
					t.Fatalf("chat send accepted fields mismatch: %#v", ack)
				}
			},
		},
		{
			name: "delivery notify",
			envelope: &ProtoServerEnvelope{Command: 37, RequestId: "notify-1",
				Payload: &ProtoServerEnvelope_ChatDeliveryNotify{ChatDeliveryNotify: &ProtoChatDeliveryNotify{
					ConversationId: "s:u1:u2", RecipientId: "u2", DeviceId: "device-2",
					DeliveredSeq: 42, UpdatedAt: 654321,
				}}},
			verify: func(t *testing.T, got *ProtoServerEnvelope) {
				notify := got.GetChatDeliveryNotify()
				if notify == nil || notify.GetDeliveredSeq() != 42 || notify.GetDeviceId() != "device-2" {
					t.Fatalf("delivery notify oneof mismatch: %#v", notify)
				}
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			encoded, err := proto.Marshal(test.envelope)
			if err != nil {
				t.Fatalf("marshal server envelope: %v", err)
			}
			got := new(ProtoServerEnvelope)
			if err := proto.Unmarshal(encoded, got); err != nil {
				t.Fatalf("unmarshal server envelope: %v", err)
			}
			test.verify(t, got)
		})
	}
}
