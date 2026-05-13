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

	TCPConnectReq                   = byte(1)
	TCPConnectSuccess               = byte(2)
	TCPAuthReq                      = byte(10)
	TCPAuthSuccess                  = byte(11)
	TCPHeartbeatReq                 = byte(20)
	TCPHeartbeatResp                = byte(21)
	TCPSendMsgReq                   = byte(30)
	TCPSendMsgResp                  = byte(31)
	TCPRecvMsgNotify                = byte(32)
	TCPRevokeMsgReq                 = byte(34)
	TCPRevokeMsgNotify              = byte(35)
	TCPForceLogoutNotify            = byte(42)
	TCPFriendApplicationNotify      = byte(70)
	TCPFriendApplicationProcessed   = byte(71)
	TCPFriendInfoChangeNotify       = byte(72)
	TCPErrorResp                    = byte(90)
)

const (
	CommandConnect        = byte(1)
	CommandAuth           = byte(10)
	CommandHeartbeat      = byte(20)
	CommandChatSend       = byte(30)
	CommandChatSendAck    = byte(31)
	CommandChatRecv       = byte(32)
	CommandChatRead       = byte(33)
	CommandChatRevoke     = byte(34)
	CommandForceLogout    = byte(35)
	CommandError          = byte(90)
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
	Version     byte
	CommandType byte
	RequestID   string
	Timestamp   int64
	Payload     []byte
}

func EncodeFrame(commandType byte, requestID string, timestamp int64, payload []byte) ([]byte, error) {
	if len(requestID) > 16 {
		return nil, ErrRequestIDTooLong
	}
	if len(payload) > MaxDataLength {
		return nil, ErrFrameTooLarge
	}
	frame := make([]byte, HeaderLength+len(payload))
	binary.BigEndian.PutUint16(frame[0:2], Magic)
	frame[2] = Version
	frame[3] = commandType
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
		Version:     raw[2],
		CommandType: raw[3],
		RequestID:   requestID,
		Timestamp:   int64(binary.BigEndian.Uint64(raw[24:32])),
		Payload:     payload,
	}, nil
}