package service

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/cheeseim/cheesebox/internal/domain"
	pb "github.com/cheeseim/cheesebox/internal/proto"
	"github.com/cheeseim/cheesebox/internal/transport/tcpim"
)

type ChatSender interface {
	SendChatMessage(requestID string, message *pb.ProtoMessage) error
}

type HistoryLoader interface {
	LoadHistoryPage(ctx context.Context, accessToken, conversationID string, limit int) ([]domain.HistoryMessage, error)
}

type ChatService struct {
	sender  ChatSender
	history HistoryLoader
}

func NewChatService(sender ChatSender, history HistoryLoader) *ChatService {
	return &ChatService{sender: sender, history: history}
}

func (s *ChatService) OpenConversation(ctx context.Context, accessToken, conversationID string, limit int) ([]domain.HistoryMessage, error) {
	return s.history.LoadHistoryPage(ctx, accessToken, conversationID, limit)
}

func (s *ChatService) SendText(requestID, conversationID, currentUserID, text string) (domain.MessageItem, error) {
	receiverID, groupID, sessionType, err := resolveChatTarget(conversationID, currentUserID)
	if err != nil {
		return domain.MessageItem{}, err
	}
	message := &pb.ProtoMessage{
		ClientMsgId: requestID,
		ReceiverId:  receiverID,
		GroupId:     groupID,
		Content:     []byte(text),
		ContentType: 101,
		SessionType: sessionType,
		SendTime:    time.Now().UnixMilli(),
	}
	if err := s.sender.SendChatMessage(requestID, message); err != nil {
		return domain.MessageItem{}, err
	}
	return domain.MessageItem{
		ID:       requestID,
		SenderID: currentUserID,
		Content:  text,
		Self:     true,
	}, nil
}

func (s *ChatService) HandleRealtimeEvent(event tcpim.Event) (domain.MessageItem, bool) {
	if event.Kind != tcpim.EventMessage || event.Message == nil {
		return domain.MessageItem{}, false
	}
	return domain.MessageItem{
		ID:       event.Message.GetServerMsgId(),
		SenderID: event.Message.GetSenderId(),
		Content:  string(event.Message.GetContent()),
		Self:     false,
	}, true
}

func (s *ChatService) ResolveRealtimeEvent(event tcpim.Event, currentUserID string) (string, domain.MessageItem, bool) {
	if event.Kind != tcpim.EventMessage || event.Message == nil {
		return "", domain.MessageItem{}, false
	}
	conversationID, err := resolveRealtimeConversationID(event.Message)
	if err != nil {
		return "", domain.MessageItem{}, false
	}
	item := domain.MessageItem{
		ID:       firstNonEmpty(event.Message.GetServerMsgId(), event.Message.GetClientMsgId()),
		SenderID: event.Message.GetSenderId(),
		Content:  string(event.Message.GetContent()),
		Self:     event.Message.GetSenderId() == currentUserID,
	}
	return conversationID, item, true
}

func resolveChatTarget(conversationID, currentUserID string) (string, string, int32, error) {
	switch {
	case strings.HasPrefix(conversationID, "g:"):
		groupID := strings.TrimPrefix(conversationID, "g:")
		if groupID == "" {
			return "", "", 0, fmt.Errorf("invalid group conversation: %s", conversationID)
		}
		return "", groupID, 2, nil
	case strings.HasPrefix(conversationID, "c2:"):
		groupID := strings.TrimPrefix(conversationID, "c2:")
		if groupID == "" {
			return "", "", 0, fmt.Errorf("invalid legacy group conversation: %s", conversationID)
		}
		return "", groupID, 2, nil
	case strings.HasPrefix(conversationID, "s:"), strings.HasPrefix(conversationID, "c1:"):
		parts := strings.Split(conversationID, ":")
		if len(parts) != 3 {
			return "", "", 0, fmt.Errorf("invalid direct conversation: %s", conversationID)
		}
		if currentUserID != "" {
			if parts[1] == currentUserID {
				return parts[2], "", 1, nil
			}
			if parts[2] == currentUserID {
				return parts[1], "", 1, nil
			}
		}
		return parts[2], "", 1, nil
	default:
		return "", "", 0, fmt.Errorf("unsupported conversation: %s", conversationID)
	}
}

func resolveRealtimeConversationID(message *pb.ProtoMessage) (string, error) {
	switch message.GetSessionType() {
	case 2:
		groupID := message.GetGroupId()
		if groupID == "" {
			return "", fmt.Errorf("invalid group message: empty group id")
		}
		return "c2:" + groupID, nil
	case 1:
		senderID := message.GetSenderId()
		receiverID := message.GetReceiverId()
		if senderID == "" || receiverID == "" {
			return "", fmt.Errorf("invalid direct message: missing sender or receiver")
		}
		if senderID <= receiverID {
			return "s:" + senderID + ":" + receiverID, nil
		}
		return "s:" + receiverID + ":" + senderID, nil
	default:
		return "", fmt.Errorf("unsupported session type: %d", message.GetSessionType())
	}
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}
