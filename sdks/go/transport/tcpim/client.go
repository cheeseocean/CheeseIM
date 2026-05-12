package tcpim

import (
	"context"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"

	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
	gproto "google.golang.org/protobuf/proto"
)

type DialFunc func(context.Context, string, string) (net.Conn, error)

type Client struct {
	dial      DialFunc
	heartbeat time.Duration

	mu     sync.Mutex
	conn   net.Conn
	events chan Event
	done   chan struct{}
}

type EventKind string

const (
	EventAuthSuccess EventKind = "auth_success"
	EventAck         EventKind = "ack"
	EventMessage     EventKind = "message"
	EventDisconnect  EventKind = "disconnect"
	EventError       EventKind = "error"
)

type Event struct {
	Kind      EventKind
	RequestID string
	UserID    string
	Ack       *pb.ProtoChatSendAck
	Message   *pb.ProtoMessage
	Err       error
}

func NewClient(dial DialFunc, heartbeat time.Duration) *Client {
	if dial == nil {
		dial = defaultDial
	}
	if heartbeat <= 0 {
		heartbeat = 30 * time.Second
	}
	return &Client{
		dial:      dial,
		heartbeat: heartbeat,
		events:    make(chan Event, 16),
	}
}

func (c *Client) Events() <-chan Event {
	return c.events
}

func (c *Client) Connect(ctx context.Context, address, ticket string) (string, error) {
	conn, err := c.dial(ctx, "tcp", address)
	if err != nil {
		return "", err
	}

	authPayload, err := gproto.Marshal(&pb.ProtoAuthRequest{Ticket: ticket})
	if err != nil {
		_ = conn.Close()
		return "", fmt.Errorf("marshal auth request: %w", err)
	}
	frame, err := EncodeFrame(TCPAuthReq, "auth", time.Now().UnixMilli(), authPayload)
	if err != nil {
		_ = conn.Close()
		return "", err
	}
	if _, err := conn.Write(frame); err != nil {
		_ = conn.Close()
		return "", fmt.Errorf("write auth frame: %w", err)
	}

	authMessage, err := c.awaitAuth(ctx, conn)
	if err != nil {
		_ = conn.Close()
		return "", err
	}

	c.mu.Lock()
	c.conn = conn
	c.done = make(chan struct{})
	c.mu.Unlock()

	go c.readLoop(conn)
	go c.heartbeatLoop(conn)
	c.emit(Event{Kind: EventAuthSuccess, RequestID: "auth", UserID: authMessage.GetUserId()})
	return authMessage.GetUserId(), nil
}

func (c *Client) SendChatMessage(requestID string, message *pb.ProtoMessage) error {
	if message == nil {
		return errors.New("tcpim: message is nil")
	}
	payload, err := gproto.Marshal(message)
	if err != nil {
		return fmt.Errorf("marshal chat message: %w", err)
	}
	frame, err := EncodeFrame(TCPSendMsgReq, requestID, time.Now().UnixMilli(), payload)
	if err != nil {
		return err
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.conn == nil {
		return errors.New("tcpim: not connected")
	}
	_, err = c.conn.Write(frame)
	return err
}

func (c *Client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.done != nil {
		close(c.done)
		c.done = nil
	}
	if c.conn == nil {
		return nil
	}
	err := c.conn.Close()
	c.conn = nil
	return err
}

func (c *Client) heartbeatLoop(conn net.Conn) {
	ticker := time.NewTicker(c.heartbeat)
	defer ticker.Stop()
	for {
		c.mu.Lock()
		done := c.done
		c.mu.Unlock()
		select {
		case <-ticker.C:
			frame, err := EncodeFrame(TCPHeartbeatReq, "heartbeat", time.Now().UnixMilli(), nil)
			if err != nil {
				c.emit(Event{Kind: EventError, Err: err})
				return
			}
			if _, err := conn.Write(frame); err != nil {
				return
			}
		case <-done:
			return
		}
	}
}

func (c *Client) readLoop(conn net.Conn) {
	defer func() {
		_ = conn.Close()
		c.mu.Lock()
		if c.conn == conn {
			c.conn = nil
		}
		if c.done != nil {
			close(c.done)
			c.done = nil
		}
		c.mu.Unlock()
		c.emit(Event{Kind: EventDisconnect})
	}()

	for {
		header := make([]byte, HeaderLength)
		if _, err := io.ReadFull(conn, header); err != nil {
			if !errors.Is(err, io.EOF) {
				c.emit(Event{Kind: EventError, Err: err})
			}
			return
		}
		payloadLength := int(binary.BigEndian.Uint32(header[4:8]))
		raw := header
		if payloadLength > 0 {
			payload := make([]byte, payloadLength)
			if _, err := io.ReadFull(conn, payload); err != nil {
				c.emit(Event{Kind: EventError, Err: err})
				return
			}
			raw = append(raw, payload...)
		}
		frame, err := DecodeFrame(raw)
		if err != nil {
			c.emit(Event{Kind: EventError, Err: err})
			return
		}
		c.handleFrame(frame)
	}
}

func (c *Client) handleFrame(frame Frame) {
	switch frame.CommandType {
	case TCPAuthSuccess:
		var message pb.ProtoAuthResponse
		if err := gproto.Unmarshal(frame.Payload, &message); err != nil {
			c.emit(Event{Kind: EventError, Err: err})
			return
		}
		c.emit(Event{Kind: EventAuthSuccess, RequestID: frame.RequestID, UserID: message.GetUserId()})
	case TCPSendMsgResp:
		var message pb.ProtoChatSendAck
		if err := gproto.Unmarshal(frame.Payload, &message); err != nil {
			c.emit(Event{Kind: EventError, Err: err})
			return
		}
		c.emit(Event{Kind: EventAck, RequestID: frame.RequestID, Ack: &message})
	case TCPRecvMsgNotify:
		var message pb.ProtoMessage
		if err := gproto.Unmarshal(frame.Payload, &message); err != nil {
			c.emit(Event{Kind: EventError, Err: err})
			return
		}
		c.emit(Event{Kind: EventMessage, RequestID: frame.RequestID, Message: &message})
	case TCPErrorResp:
		c.emit(Event{Kind: EventError, RequestID: frame.RequestID, Err: errors.New(string(frame.Payload))})
	}
}

func (c *Client) emit(event Event) {
	select {
	case c.events <- event:
	default:
	}
}

func defaultDial(ctx context.Context, network, address string) (net.Conn, error) {
	var dialer net.Dialer
	return dialer.DialContext(ctx, network, address)
}

func (c *Client) awaitAuth(ctx context.Context, conn net.Conn) (*pb.ProtoAuthResponse, error) {
	if deadline, ok := ctx.Deadline(); ok {
		if err := conn.SetDeadline(deadline); err != nil {
			return nil, err
		}
		defer func() {
			_ = conn.SetDeadline(time.Time{})
		}()
	}

	for {
		header := make([]byte, HeaderLength)
		if _, err := io.ReadFull(conn, header); err != nil {
			return nil, fmt.Errorf("read auth header: %w", err)
		}
		payloadLength := int(binary.BigEndian.Uint32(header[4:8]))
		raw := header
		if payloadLength > 0 {
			payload := make([]byte, payloadLength)
			if _, err := io.ReadFull(conn, payload); err != nil {
				return nil, fmt.Errorf("read auth payload: %w", err)
			}
			raw = append(raw, payload...)
		}
		frame, err := DecodeFrame(raw)
		if err != nil {
			return nil, err
		}
		switch frame.CommandType {
		case TCPConnectSuccess:
			continue
		case TCPAuthSuccess:
			var message pb.ProtoAuthResponse
			if err := gproto.Unmarshal(frame.Payload, &message); err != nil {
				return nil, err
			}
			return &message, nil
		case TCPErrorResp:
			return nil, errors.New(string(frame.Payload))
		default:
			return nil, fmt.Errorf("unexpected auth frame type: %d", frame.CommandType)
		}
	}
}
