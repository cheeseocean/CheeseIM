package tcpim

import (
	"bytes"
	"encoding/binary"
	"errors"
	"io"
	"reflect"
	"strconv"
	"testing"
	"testing/iotest"

	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
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
			if frame.CommandType != tt.msgType {
				t.Fatalf("CommandType = %d, want %d", frame.CommandType, tt.msgType)
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

func TestReadFrameRejectsInvalidHeaderBeforeReadingBody(t *testing.T) {
	for _, tt := range invalidFrameHeaders() {
		t.Run(tt.name, func(t *testing.T) {
			reader := &headerOnlyReader{header: bytes.NewReader(tt.header)}
			if _, err := readFrame(reader); !errors.Is(err, tt.wantErr) {
				t.Fatalf("readFrame() error = %v, want %v", err, tt.wantErr)
			}
			if reader.bodyReads != 0 {
				t.Fatalf("body reads = %d, want 0", reader.bodyReads)
			}
			if reader.header.Len() != 0 {
				t.Fatal("readFrame() did not read the complete header")
			}
			if _, err := DecodeFrame(tt.header); !errors.Is(err, tt.wantErr) {
				t.Fatalf("DecodeFrame() error = %v, want %v", err, tt.wantErr)
			}
		})
	}
}

func TestReadFrameMatchesDecodeFrameAndLeavesNextFrameUnread(t *testing.T) {
	for _, size := range []int{0, 3, MaxDataLength} {
		t.Run(strconv.Itoa(size), func(t *testing.T) {
			payload := bytes.Repeat([]byte{0xA5}, size)
			raw, err := EncodeFrame(TCPRecvMsgNotify, "notify-1", 1710000000000, payload)
			if err != nil {
				t.Fatal(err)
			}
			next, err := EncodeFrame(TCPHeartbeatResp, "heartbeat", 1710000000001, nil)
			if err != nil {
				t.Fatal(err)
			}
			reader := bytes.NewReader(append(raw, next...))
			frame, err := readFrame(iotest.OneByteReader(reader))
			if err != nil {
				t.Fatalf("readFrame() error = %v", err)
			}
			decoded, err := DecodeFrame(raw)
			if err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(frame, decoded) || !bytes.Equal(frame.Payload, payload) {
				t.Fatal("stream and memory decoding differ")
			}
			if reader.Len() != len(next) {
				t.Fatalf("unread bytes = %d, want next frame length %d", reader.Len(), len(next))
			}
			nextFrame, err := readFrame(reader)
			if err != nil || nextFrame.CommandType != TCPHeartbeatResp || nextFrame.RequestID != "heartbeat" {
				t.Fatalf("next frame = %#v, error = %v", nextFrame, err)
			}
		})
	}
}

func TestReadFrameRejectsTruncatedHeaderAndBody(t *testing.T) {
	raw, err := EncodeFrame(TCPAuthSuccess, "auth", 123, []byte{1, 2, 3})
	if err != nil {
		t.Fatal(err)
	}
	tests := []struct {
		name    string
		length  int
		wantErr error
	}{
		{name: "empty stream", length: 0, wantErr: io.EOF},
		{name: "partial header", length: HeaderLength - 1, wantErr: io.ErrUnexpectedEOF},
		{name: "missing body", length: HeaderLength, wantErr: io.ErrUnexpectedEOF},
		{name: "partial body", length: len(raw) - 1, wantErr: io.ErrUnexpectedEOF},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := readFrame(bytes.NewReader(raw[:tt.length])); !errors.Is(err, tt.wantErr) {
				t.Fatalf("readFrame() error = %v, want %v", err, tt.wantErr)
			}
			if _, err := DecodeFrame(raw[:tt.length]); !errors.Is(err, ErrTruncatedFrame) {
				t.Fatalf("DecodeFrame() error = %v, want ErrTruncatedFrame", err)
			}
		})
	}
}

// headerOnlyReader 将任何消息体读取记录为失败，证明拒绝不依赖远端继续发送或关闭连接。
type headerOnlyReader struct {
	header    *bytes.Reader
	bodyReads int
}

func (r *headerOnlyReader) Read(p []byte) (int, error) {
	if r.header.Len() == 0 {
		r.bodyReads++
		return 0, errors.New("unexpected body read")
	}
	return r.header.Read(p)
}

func invalidFrameHeaders() []struct {
	name    string
	header  []byte
	wantErr error
} {
	newHeader := func(length uint32) []byte {
		header := make([]byte, HeaderLength)
		binary.BigEndian.PutUint16(header[0:2], Magic)
		header[2] = Version
		header[3] = TCPAuthSuccess
		binary.BigEndian.PutUint32(header[4:8], length)
		return header
	}
	badMagic := newHeader(1)
	binary.BigEndian.PutUint16(badMagic[0:2], 0xFFFF)
	badVersion := newHeader(1)
	badVersion[2] = Version + 1
	return []struct {
		name    string
		header  []byte
		wantErr error
	}{
		{name: "bad magic", header: badMagic, wantErr: ErrInvalidMagic},
		{name: "bad version", header: badVersion, wantErr: ErrInvalidVersion},
		{name: "over limit", header: newHeader(MaxDataLength + 1), wantErr: ErrFrameTooLarge},
		{name: "high bit length", header: newHeader(1 << 31), wantErr: ErrFrameTooLarge},
		{name: "max uint32 length", header: newHeader(^uint32(0)), wantErr: ErrFrameTooLarge},
	}
}

func mustUnmarshal(t *testing.T, payload []byte, message gproto.Message) {
	t.Helper()
	if err := gproto.Unmarshal(payload, message); err != nil {
		t.Fatalf("Unmarshal() error = %v", err)
	}
}
