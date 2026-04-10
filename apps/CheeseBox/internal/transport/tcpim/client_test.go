package tcpim

import (
	"context"
	"errors"
	"io"
	"net"
	"testing"
	"time"

	pb "github.com/cheeseim/cheesebox/internal/proto"
	gproto "google.golang.org/protobuf/proto"
)

func TestClientConnectEmitsAuthSuccess(t *testing.T) {
	clientConn, serverConn := net.Pipe()
	defer serverConn.Close()

	client := NewClient(pipeDialer(clientConn), time.Hour)
	go func() {
		frame := mustReadFrame(t, serverConn)
		if frame.MsgType != TCPAuthReq {
			t.Errorf("MsgType = %d, want %d", frame.MsgType, TCPAuthReq)
		}
		authPayload := mustMarshal(t, &pb.ProtoAuthResponse{UserId: "user-1", Message: "ok"})
		writeFrame(t, serverConn, TCPAuthSuccess, "auth", authPayload)
	}()

	userID, err := client.Connect(context.Background(), "ignored", "ticket-1")
	if err != nil {
		t.Fatalf("Connect() error = %v", err)
	}
	if userID != "user-1" {
		t.Fatalf("userID = %q, want user-1", userID)
	}

	event := waitEvent(t, client.Events())
	if event.Kind != EventAuthSuccess || event.UserID != "user-1" {
		t.Fatalf("event = %#v, want auth success for user-1", event)
	}
	_ = client.Close()
}

func TestClientSendChatMessageEmitsAck(t *testing.T) {
	clientConn, serverConn := net.Pipe()
	defer serverConn.Close()

	client := NewClient(pipeDialer(clientConn), time.Hour)
	go func() {
		_ = mustReadFrame(t, serverConn)
		writeFrame(t, serverConn, TCPAuthSuccess, "auth", mustMarshal(t, &pb.ProtoAuthResponse{UserId: "user-1"}))
		frame := mustReadFrame(t, serverConn)
		if frame.MsgType != TCPSendMsgReq {
			t.Errorf("MsgType = %d, want %d", frame.MsgType, TCPSendMsgReq)
		}
		writeFrame(t, serverConn, TCPSendMsgResp, frame.RequestID, mustMarshal(t, &pb.ProtoChatSendAck{
			ServerMsgId: "server-1",
			ClientMsgId: "client-1",
			SendTime:    123,
		}))
	}()

	if _, err := client.Connect(context.Background(), "ignored", "ticket-1"); err != nil {
		t.Fatalf("Connect() error = %v", err)
	}
	_ = waitEvent(t, client.Events())
	if err := client.SendChatMessage("send-1", &pb.ProtoMessage{ClientMsgId: "client-1", Content: []byte("hello")}); err != nil {
		t.Fatalf("SendChatMessage() error = %v", err)
	}
	event := waitEvent(t, client.Events())
	if event.Kind != EventAck || event.Ack.GetServerMsgId() != "server-1" {
		t.Fatalf("event = %#v, want ack", event)
	}
	_ = client.Close()
}

func TestClientReceivesInboundMessage(t *testing.T) {
	clientConn, serverConn := net.Pipe()
	defer serverConn.Close()

	client := NewClient(pipeDialer(clientConn), time.Hour)
	go func() {
		_ = mustReadFrame(t, serverConn)
		writeFrame(t, serverConn, TCPAuthSuccess, "auth", mustMarshal(t, &pb.ProtoAuthResponse{UserId: "user-1"}))
		writeFrame(t, serverConn, TCPRecvMsgNotify, "notify-1", mustMarshal(t, &pb.ProtoMessage{
			ServerMsgId: "server-2",
			SenderId:    "user-2",
			ReceiverId:  "user-1",
			Content:     []byte("hello"),
		}))
	}()

	if _, err := client.Connect(context.Background(), "ignored", "ticket-1"); err != nil {
		t.Fatalf("Connect() error = %v", err)
	}
	_ = waitEvent(t, client.Events())
	event := waitEvent(t, client.Events())
	if event.Kind != EventMessage || string(event.Message.GetContent()) != "hello" {
		t.Fatalf("event = %#v, want inbound message", event)
	}
	_ = client.Close()
}

func TestClientSurfacesDisconnectAndErrors(t *testing.T) {
	t.Run("disconnect", func(t *testing.T) {
		clientConn, serverConn := net.Pipe()
		client := NewClient(pipeDialer(clientConn), time.Hour)
		go func() {
			_ = mustReadFrame(t, serverConn)
			writeFrame(t, serverConn, TCPAuthSuccess, "auth", mustMarshal(t, &pb.ProtoAuthResponse{UserId: "user-1"}))
			_ = serverConn.Close()
		}()

		if _, err := client.Connect(context.Background(), "ignored", "ticket-1"); err != nil {
			t.Fatalf("Connect() error = %v", err)
		}
		_ = waitEvent(t, client.Events())
		event := waitEvent(t, client.Events())
		if event.Kind != EventDisconnect && event.Kind != EventError {
			t.Fatalf("event = %#v, want disconnect or error", event)
		}
		_ = client.Close()
	})

	t.Run("decode error", func(t *testing.T) {
		clientConn, serverConn := net.Pipe()
		client := NewClient(pipeDialer(clientConn), time.Hour)
		go func() {
			_ = mustReadFrame(t, serverConn)
			_, _ = serverConn.Write([]byte("bad"))
			_ = serverConn.Close()
		}()
		if _, err := client.Connect(context.Background(), "ignored", "ticket-1"); err == nil {
			t.Fatal("Connect() error = nil, want non-nil")
		}
		_ = client.Close()
	})
}

func pipeDialer(conn net.Conn) DialFunc {
	return func(context.Context, string, string) (net.Conn, error) {
		return conn, nil
	}
}

func waitEvent(t *testing.T, ch <-chan Event) Event {
	t.Helper()
	select {
	case event := <-ch:
		return event
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for event")
		return Event{}
	}
}

func mustReadFrame(t *testing.T, conn net.Conn) Frame {
	t.Helper()
	header := make([]byte, HeaderLength)
	if _, err := io.ReadFull(conn, header); err != nil {
		t.Fatalf("ReadFull(header) error = %v", err)
	}
	payloadLength := int(uint32(header[4])<<24 | uint32(header[5])<<16 | uint32(header[6])<<8 | uint32(header[7]))
	payload := make([]byte, payloadLength)
	if _, err := io.ReadFull(conn, payload); err != nil {
		t.Fatalf("ReadFull(payload) error = %v", err)
	}
	frame, err := DecodeFrame(append(header, payload...))
	if err != nil {
		t.Fatalf("DecodeFrame() error = %v", err)
	}
	return frame
}

func writeFrame(t *testing.T, conn net.Conn, msgType byte, requestID string, payload []byte) {
	t.Helper()
	frame, err := EncodeFrame(msgType, requestID, time.Now().UnixMilli(), payload)
	if err != nil {
		t.Fatalf("EncodeFrame() error = %v", err)
	}
	if _, err := conn.Write(frame); err != nil && !errors.Is(err, net.ErrClosed) {
		t.Fatalf("Write() error = %v", err)
	}
}

func mustMarshal(t *testing.T, message gproto.Message) []byte {
	t.Helper()
	payload, err := gproto.Marshal(message)
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
	return payload
}
