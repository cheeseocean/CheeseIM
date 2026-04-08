package tcpim

import (
	"encoding/binary"
	"errors"
	"testing"

	pb "github.com/cheeseim/cheesebox/internal/proto"
	gproto "google.golang.org/protobuf/proto"
)

func TestEncodeFrame_AuthRequestMatchesHeaderLayout(t *testing.T) {
	auth := &pb.ProtoAuthRequest{Ticket: "ticket-1"}
	payload, err := gproto.Marshal(auth)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}

	frame, err := EncodeFrame(TCPAuthReq, "op-auth-1", 1710000000000, payload)
	if err != nil {
		t.Fatalf("EncodeFrame() error = %v", err)
	}

	if got := binary.BigEndian.Uint16(frame[0:2]); got != Magic {
		t.Fatalf("magic = %#x, want %#x", got, Magic)
	}
	if got := frame[2]; got != Version {
		t.Fatalf("version = %d, want %d", got, Version)
	}
	if got := frame[3]; got != TCPAuthReq {
		t.Fatalf("msgType = %d, want %d", got, TCPAuthReq)
	}
	if got := binary.BigEndian.Uint32(frame[4:8]); got != uint32(len(payload)) {
		t.Fatalf("dataLength = %d, want %d", got, len(payload))
	}
	if got := string(frame[8 : 8+len("op-auth-1")]); got != "op-auth-1" {
		t.Fatalf("requestID bytes = %q, want %q", got, "op-auth-1")
	}
}

func TestDecodeFrame_ParsesServerPayloads(t *testing.T) {
	tests := []struct {
		name      string
		msgType   byte
		requestID string
		payload   gproto.Message
		assert    func(t *testing.T, frame Frame)
	}{
		{
			name:      "connect",
			msgType:   TCPConnectSuccess,
			requestID: "system",
			payload:   &pb.ProtoConnectResponse{ConnId: "conn-1", Message: "connected"},
			assert: func(t *testing.T, frame Frame) {
				var message pb.ProtoConnectResponse
				mustUnmarshal(t, frame.Payload, &message)
				if message.GetConnId() != "conn-1" {
					t.Fatalf("ConnId = %q, want conn-1", message.GetConnId())
				}
			},
		},
		{
			name:      "auth",
			msgType:   TCPAuthSuccess,
			requestID: "op-auth-1",
			payload:   &pb.ProtoAuthResponse{UserId: "user-1", Message: "ok"},
			assert: func(t *testing.T, frame Frame) {
				var message pb.ProtoAuthResponse
				mustUnmarshal(t, frame.Payload, &message)
				if message.GetUserId() != "user-1" {
					t.Fatalf("UserId = %q, want user-1", message.GetUserId())
				}
			},
		},
		{
			name:      "chat send ack",
			msgType:   TCPSendMsgResp,
			requestID: "op-send-1",
			payload:   &pb.ProtoChatSendAck{ServerMsgId: "server-1", ClientMsgId: "client-1", SendTime: 1710000000000},
			assert: func(t *testing.T, frame Frame) {
				var message pb.ProtoChatSendAck
				mustUnmarshal(t, frame.Payload, &message)
				if message.GetServerMsgId() != "server-1" {
					t.Fatalf("ServerMsgId = %q, want server-1", message.GetServerMsgId())
				}
			},
		},
		{
			name:      "chat recv notify",
			msgType:   TCPRecvMsgNotify,
			requestID: "op-notify-1",
			payload:   &pb.ProtoMessage{ServerMsgId: "server-2", SenderId: "user-a", ReceiverId: "user-b", Content: []byte("hello")},
			assert: func(t *testing.T, frame Frame) {
				var message pb.ProtoMessage
				mustUnmarshal(t, frame.Payload, &message)
				if got := string(message.GetContent()); got != "hello" {
					t.Fatalf("Content = %q, want hello", got)
				}
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			payload, err := gproto.Marshal(tt.payload)
			if err != nil {
				t.Fatalf("Marshal() error = %v", err)
			}
			raw, err := EncodeFrame(tt.msgType, tt.requestID, 1710000000000, payload)
			if err != nil {
				t.Fatalf("EncodeFrame() error = %v", err)
			}
			frame, err := DecodeFrame(raw)
			if err != nil {
				t.Fatalf("DecodeFrame() error = %v", err)
			}
			if frame.MsgType != tt.msgType {
				t.Fatalf("MsgType = %d, want %d", frame.MsgType, tt.msgType)
			}
			if frame.RequestID != tt.requestID {
				t.Fatalf("RequestID = %q, want %q", frame.RequestID, tt.requestID)
			}
			tt.assert(t, frame)
		})
	}
}

func TestDecodeFrame_RejectsInvalidMagicAndTruncatedPayload(t *testing.T) {
	t.Run("invalid magic", func(t *testing.T) {
		raw := make([]byte, HeaderLength)
		binary.BigEndian.PutUint16(raw[0:2], 0xFFFF)
		raw[2] = Version
		if _, err := DecodeFrame(raw); !errors.Is(err, ErrInvalidMagic) {
			t.Fatalf("DecodeFrame() error = %v, want ErrInvalidMagic", err)
		}
	})

	t.Run("truncated payload", func(t *testing.T) {
		raw, err := EncodeFrame(TCPAuthReq, "op-auth-1", 1710000000000, []byte{1, 2, 3})
		if err != nil {
			t.Fatalf("EncodeFrame() error = %v", err)
		}
		raw = raw[:len(raw)-1]
		if _, err := DecodeFrame(raw); !errors.Is(err, ErrTruncatedFrame) {
			t.Fatalf("DecodeFrame() error = %v, want ErrTruncatedFrame", err)
		}
	})
}

func TestMsgTypeMappings(t *testing.T) {
	if got, err := ClientMsgTypeForCommand(CommandAuth); err != nil || got != TCPAuthReq {
		t.Fatalf("ClientMsgTypeForCommand(auth) = (%d, %v), want (%d, nil)", got, err, TCPAuthReq)
	}
	if got, err := ServerMsgTypeForCommand(CommandChatRecv); err != nil || got != TCPRecvMsgNotify {
		t.Fatalf("ServerMsgTypeForCommand(chat recv) = (%d, %v), want (%d, nil)", got, err, TCPRecvMsgNotify)
	}
	if _, err := ClientMsgTypeForCommand(999); !errors.Is(err, ErrUnknownCommand) {
		t.Fatalf("ClientMsgTypeForCommand(999) error = %v, want ErrUnknownCommand", err)
	}
}

func mustUnmarshal(t *testing.T, payload []byte, message gproto.Message) {
	t.Helper()
	if err := gproto.Unmarshal(payload, message); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
}
