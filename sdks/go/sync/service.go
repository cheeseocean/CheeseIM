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
	api                PullAPI
	accessToken        string
	mu                 stdsync.RWMutex
	syncedMaxSeqs      map[string]int64
	serverMaxSeqs      map[string]int64
	conversationCursor types.ConversationSyncCursor
}

func NewService(api PullAPI) *Service {
	return &Service{
		api:           api,
		syncedMaxSeqs: make(map[string]int64),
		serverMaxSeqs: make(map[string]int64),
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
	s.serverMaxSeqs = make(map[string]int64)
	s.conversationCursor = types.ConversationSyncCursor{}
}

// Bootstrap 初始化同步状态
func (s *Service) Bootstrap(data types.BootstrapData) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.syncedMaxSeqs = make(map[string]int64)
	s.serverMaxSeqs = make(map[string]int64, len(data.MaxSeqs))
	for conversationID, seq := range data.MaxSeqs {
		s.serverMaxSeqs[conversationID] = seq
	}
	s.conversationCursor = data.ConversationCursor
}

// GetSyncedMaxSeq 获取会话已同步的最大序列号
func (s *Service) GetSyncedMaxSeq(conversationID string) int64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.syncedMaxSeqs[conversationID]
}

// GetServerMaxSeq 获取服务端当前最大序列号快照。
func (s *Service) GetServerMaxSeq(conversationID string) int64 {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.serverMaxSeqs[conversationID]
}

// UpdateSyncedMaxSeq 更新会话已同步的最大序列号
func (s *Service) UpdateSyncedMaxSeq(conversationID string, seq int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if seq > s.syncedMaxSeqs[conversationID] {
		s.syncedMaxSeqs[conversationID] = seq
	}
}

// UpdateServerMaxSeq 更新服务端最大序列号快照。
func (s *Service) UpdateServerMaxSeq(conversationID string, seq int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if seq > s.serverMaxSeqs[conversationID] {
		s.serverMaxSeqs[conversationID] = seq
	}
}

// GetConversationCursor 获取会话元数据同步游标。
func (s *Service) GetConversationCursor() types.ConversationSyncCursor {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.conversationCursor
}

// UpdateConversationCursor 更新会话元数据同步游标。
func (s *Service) UpdateConversationCursor(cursor types.ConversationSyncCursor) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if cursor.Version > s.conversationCursor.Version || cursor.VersionID != s.conversationCursor.VersionID {
		s.conversationCursor = cursor
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
