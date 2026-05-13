package imsync

import (
	"context"
	stdsync "sync"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

// PullAPI 拉取消息接口
type PullAPI interface {
	PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limitPerConversation int64) ([]types.PulledConversationMessages, error)
	AckReadSeq(ctx context.Context, accessToken, conversationID string, readSeq int64) error
}

// Service 同步服务，只负责底层 API 调用
type Service struct {
	api           PullAPI
	accessToken   string
	mu            stdsync.RWMutex
	syncedMaxSeqs map[string]int64
}

func NewService(api PullAPI) *Service {
	return &Service{
		api:           api,
		syncedMaxSeqs: make(map[string]int64),
	}
}

// SetAccessToken 设置访问令牌
func (s *Service) SetAccessToken(token string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.accessToken = token
}

// Reset 重置状态
func (s *Service) Reset() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.syncedMaxSeqs = make(map[string]int64)
}

// Bootstrap 初始化同步状态
func (s *Service) Bootstrap(data types.BootstrapData) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.syncedMaxSeqs = make(map[string]int64, len(data.MaxSeqs))
	for conversationID, seq := range data.MaxSeqs {
		s.syncedMaxSeqs[conversationID] = seq
	}
}

// GetSyncedMaxSeq 获取会话已同步的最大序列号
func (s *Service) GetSyncedMaxSeq(conversationID string) int64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.syncedMaxSeqs[conversationID]
}

// UpdateSyncedMaxSeq 更新会话已同步的最大序列号
func (s *Service) UpdateSyncedMaxSeq(conversationID string, seq int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if seq > s.syncedMaxSeqs[conversationID] {
		s.syncedMaxSeqs[conversationID] = seq
	}
}

// PullMessages 拉取指定范围的消息
func (s *Service) PullMessages(ctx context.Context, ranges []types.SeqRange, limitPerConversation int64) ([]types.PulledConversationMessages, error) {
	s.mu.RLock()
	token := s.accessToken
	s.mu.RUnlock()
	return s.api.PullMessages(ctx, token, ranges, limitPerConversation)
}

// MarkRead 标记已读
func (s *Service) MarkRead(ctx context.Context, conversationID string, readSeq int64) error {
	s.mu.RLock()
	token := s.accessToken
	s.mu.RUnlock()
	return s.api.AckReadSeq(ctx, token, conversationID, readSeq)
}
