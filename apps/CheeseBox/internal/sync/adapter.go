package sync

import (
	"context"

	sdkclient "github.com/cheeseim/cheeseim-go-sdk/client"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

// SDKPuller SDK 拉取器适配器
type SDKPuller struct {
	client *sdkclient.Client
}

func NewSDKPuller(client *sdkclient.Client) *SDKPuller {
	return &SDKPuller{client: client}
}

func (p *SDKPuller) PullMessages(ctx context.Context, ranges []sdktypes.SeqRange, limitPerConversation int64) ([]sdktypes.PulledConversationMessages, error) {
	return p.client.PullMessages(ctx, ranges, limitPerConversation)
}

func (p *SDKPuller) GetSyncedMaxSeq(conversationID string) int64 {
	return p.client.GetSyncedMaxSeq(conversationID)
}

// MemoryStore 内存消息存储
type MemoryStore struct {
	messagesByConv map[string][]sdktypes.Message
}

func NewMemoryStore() *MemoryStore {
	return &MemoryStore{
		messagesByConv: make(map[string][]sdktypes.Message),
	}
}

func (s *MemoryStore) GetMessages(conversationID string) []sdktypes.Message {
	return append([]sdktypes.Message(nil), s.messagesByConv[conversationID]...)
}

func (s *MemoryStore) AppendMessage(conversationID string, msg sdktypes.Message) {
	s.messagesByConv[conversationID] = append(s.messagesByConv[conversationID], msg)
}

func (s *MemoryStore) SetMessages(conversationID string, msgs []sdktypes.Message) {
	s.messagesByConv[conversationID] = append([]sdktypes.Message(nil), msgs...)
}

func (s *MemoryStore) Clear(conversationID string) {
	delete(s.messagesByConv, conversationID)
}
