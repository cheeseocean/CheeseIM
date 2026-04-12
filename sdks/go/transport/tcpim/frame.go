package tcpim

import (
	"encoding/binary"
	"errors"
	"fmt"
	"strings"
)

const (
	Magic         = uint16(0xCEEE)
	Version       = byte(0x01)
	HeaderLength  = 32
	MaxDataLength = 1024 * 1024

	TCPConnectReq     = byte(1)
	TCPConnectSuccess = byte(2)
	TCPAuthReq        = byte(10)
	TCPAuthSuccess    = byte(11)
	TCPHeartbeatReq   = byte(20)
	TCPHeartbeatResp  = byte(21)
	TCPSendMsgReq     = byte(30)
	TCPSendMsgResp    = byte(31)
	TCPRecvMsgNotify  = byte(32)
	TCPErrorResp      = byte(90)
)

const (
	CommandConnect   = int32(1)
	CommandAuth      = int32(10)
	CommandHeartbeat = int32(20)
	CommandChatSend  = int32(30)
	CommandChatRecv  = int32(32)
	CommandError     = int32(90)
)

var (
	ErrInvalidMagic     = errors.New("tcpim: invalid magic")
	ErrInvalidVersion   = errors.New("tcpim: invalid version")
	ErrTruncatedFrame   = errors.New("tcpim: truncated frame")
	ErrFrameTooLarge    = errors.New("tcpim: frame too large")
	ErrUnknownCommand   = errors.New("tcpim: unknown command")
	ErrRequestIDTooLong = errors.New("tcpim: request id too long")
)

type Frame struct {
	Version   byte
	MsgType   byte
	RequestID string
	Timestamp int64
	Payload   []byte
}

func EncodeFrame(msgType byte, requestID string, timestamp int64, payload []byte) ([]byte, error) {
	if len(requestID) > 16 {
		return nil, ErrRequestIDTooLong
	}
	if len(payload) > MaxDataLength {
		return nil, ErrFrameTooLarge
	}
	frame := make([]byte, HeaderLength+len(payload))
	binary.BigEndian.PutUint16(frame[0:2], Magic)
	frame[2] = Version
	frame[3] = msgType
	binary.BigEndian.PutUint32(frame[4:8], uint32(len(payload)))
	copy(frame[8:24], []byte(requestID))
	binary.BigEndian.PutUint64(frame[24:32], uint64(timestamp))
	copy(frame[32:], payload)
	return frame, nil
}

func DecodeFrame(raw []byte) (Frame, error) {
	if len(raw) < HeaderLength {
		return Frame{}, ErrTruncatedFrame
	}
	if binary.BigEndian.Uint16(raw[0:2]) != Magic {
		return Frame{}, ErrInvalidMagic
	}
	if raw[2] != Version {
		return Frame{}, fmt.Errorf("%w: %d", ErrInvalidVersion, raw[2])
	}
	dataLength := int(binary.BigEndian.Uint32(raw[4:8]))
	if dataLength > MaxDataLength {
		return Frame{}, ErrFrameTooLarge
	}
	if len(raw) < HeaderLength+dataLength {
		return Frame{}, ErrTruncatedFrame
	}
	requestID := strings.TrimRight(string(raw[8:24]), "\x00 ")
	payload := make([]byte, dataLength)
	copy(payload, raw[32:32+dataLength])
	return Frame{
		Version:   raw[2],
		MsgType:   raw[3],
		RequestID: requestID,
		Timestamp: int64(binary.BigEndian.Uint64(raw[24:32])),
		Payload:   payload,
	}, nil
}

func ClientMsgTypeForCommand(command int32) (byte, error) {
	switch command {
	case CommandConnect:
		return TCPConnectReq, nil
	case CommandAuth:
		return TCPAuthReq, nil
	case CommandHeartbeat:
		return TCPHeartbeatReq, nil
	case CommandChatSend:
		return TCPSendMsgReq, nil
	default:
		return 0, fmt.Errorf("%w: %d", ErrUnknownCommand, command)
	}
}

func ServerMsgTypeForCommand(command int32) (byte, error) {
	switch command {
	case CommandConnect:
		return TCPConnectSuccess, nil
	case CommandAuth:
		return TCPAuthSuccess, nil
	case CommandHeartbeat:
		return TCPHeartbeatResp, nil
	case CommandChatSend:
		return TCPSendMsgResp, nil
	case CommandChatRecv:
		return TCPRecvMsgNotify, nil
	case CommandError:
		return TCPErrorResp, nil
	default:
		return 0, fmt.Errorf("%w: %d", ErrUnknownCommand, command)
	}
}
