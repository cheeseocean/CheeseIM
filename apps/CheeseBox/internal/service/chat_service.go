package service

import (
	"context"
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

func (s *ChatService) SendText(requestID, conversationID, senderID, receiverID, groupID, text string, sessionType int32) (domain.MessageItem, error) {
	message := &pb.ProtoMessage{
		ClientMsgId: requestID,
		SenderId:    senderID,
		ReceiverId:  receiverID,
		GroupId:     groupID,
		Content:     []byte(text),
		SessionType: sessionType,
		SendTime:    time.Now().UnixMilli(),
	}
	if err := s.sender.SendChatMessage(requestID, message); err != nil {
		return domain.MessageItem{}, err
	}
	return domain.MessageItem{
		ID:       requestID,
		SenderID: senderID,
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
