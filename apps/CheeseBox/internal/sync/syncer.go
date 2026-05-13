package sync

import (
	"context"
	"fmt"
	"sort"

	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

// MessageStore 消息存储接口
type MessageStore interface {
	GetMessages(conversationID string) []sdktypes.Message
	AppendMessage(conversationID string, msg sdktypes.Message)
	SetMessages(conversationID string, msgs []sdktypes.Message)
	Clear(conversationID string)
}

// Syncer 消息同步器，负责消息合并、gap repair 等业务逻辑
type Syncer struct {
	store        MessageStore
	puller       Puller
	getMaxSeq    func(conversationID string) int64
	updateMaxSeq func(conversationID string, seq int64)
}

type Puller interface {
	PullMessages(ctx context.Context, ranges []sdktypes.SeqRange, limitPerConversation int64) ([]sdktypes.PulledConversationMessages, error)
	GetSyncedMaxSeq(conversationID string) int64
}

// NewSyncer 创建同步器
func NewSyncer(store MessageStore, puller Puller, getMaxSeq func(conversationID string) int64, updateMaxSeq func(conversationID string, seq int64)) *Syncer {
	return &Syncer{
		store:        store,
		puller:       puller,
		getMaxSeq:    getMaxSeq,
		updateMaxSeq: updateMaxSeq,
	}
}

// HandleRealtime 处理实时消息，返回是否有消息修复
func (s *Syncer) HandleRealtime(ctx context.Context, message sdktypes.Message) (bool, error) {
	conversationID := resolveConversationID(message)
	localMessages := s.store.GetMessages(conversationID)
	localMax := currentMaxSeq(localMessages)

	// 检查是否有消息空洞需要修复
	if message.Sequence > 0 && localMax > 0 && message.Sequence > localMax+1 {
		// 有空洞，需要拉取缺失的消息
		if err := s.repairGap(ctx, conversationID, localMax+1, message.Sequence-1); err != nil {
			return false, err
		}
		// 更新本地最大序列号
		s.updateMaxSeq(conversationID, message.Sequence-1)
	}

	// 追加当前消息
	s.store.AppendMessage(conversationID, message)

	// 更新已同步的最大序列号
	if message.Sequence > 0 && message.Sequence > s.getMaxSeq(conversationID) {
		s.updateMaxSeq(conversationID, message.Sequence)
	}

	return localMax > 0 && message.Sequence > localMax+1, nil
}

// repairGap 修复消息空洞
func (s *Syncer) repairGap(ctx context.Context, conversationID string, beginSeq, endSeq int64) error {
	ranges := []sdktypes.SeqRange{
		{ConversationID: conversationID, BeginSeq: beginSeq, EndSeq: endSeq},
	}
	pulled, err := s.puller.PullMessages(ctx, ranges, endSeq-beginSeq+1)
	if err != nil {
		return fmt.Errorf("repair gap failed: %w", err)
	}
	for _, conv := range pulled {
		if conv.ConversationID == conversationID && len(conv.Messages) > 0 {
			// 合并拉取的消息
			existing := s.store.GetMessages(conversationID)
			merged := mergeMessages(existing, conv.Messages)
			s.store.SetMessages(conversationID, merged)
		}
	}
	return nil
}

// OpenConversation 打开会话，加载历史消息
func (s *Syncer) OpenConversation(ctx context.Context, conversationID string, limit int) ([]sdktypes.Message, error) {
	if limit <= 0 {
		limit = 50
	}

	// 从本地获取消息
	localMessages := s.store.GetMessages(conversationID)
	localMax := currentMaxSeq(localMessages)
	serverMax := s.getMaxSeq(conversationID)

	// 如果本地消息已过期或不完整，需要重新拉取
	needPull := len(localMessages) == 0 || (serverMax > 0 && localMax < serverMax)

	if needPull {
		beginSeq := localMax + 1
		if beginSeq < 1 {
			beginSeq = 1
		}
		ranges := []sdktypes.SeqRange{
			{ConversationID: conversationID, BeginSeq: beginSeq, EndSeq: serverMax},
		}
		pulled, err := s.puller.PullMessages(ctx, ranges, int64(limit))
		if err != nil {
			return nil, fmt.Errorf("pull messages failed: %w", err)
		}
		var pulledMessages []sdktypes.Message
		for _, conv := range pulled {
			if conv.ConversationID == conversationID {
				pulledMessages = conv.Messages
				break
			}
		}
		if len(localMessages) == 0 {
			s.store.SetMessages(conversationID, pulledMessages)
			return pulledMessages, nil
		}
		merged := mergeMessages(localMessages, pulledMessages)
		s.store.SetMessages(conversationID, merged)
		return merged, nil
	}
	return localMessages, nil
}

func mergeMessages(existing []sdktypes.Message, incoming []sdktypes.Message) []sdktypes.Message {
	merged := make([]sdktypes.Message, 0, len(existing)+len(incoming))
	seen := make(map[string]struct{}, len(existing)+len(incoming))
	add := func(item sdktypes.Message) {
		key := messageKey(item)
		if _, ok := seen[key]; ok {
			return
		}
		seen[key] = struct{}{}
		merged = append(merged, item)
	}
	for _, item := range existing {
		add(item)
	}
	for _, item := range incoming {
		add(item)
	}
	sort.Slice(merged, func(i, j int) bool {
		if merged[i].Sequence == merged[j].Sequence {
			return merged[i].SendTime < merged[j].SendTime
		}
		return merged[i].Sequence < merged[j].Sequence
	})
	return merged
}

func messageKey(item sdktypes.Message) string {
	if item.ServerMsgID != "" {
		return "server:" + item.ServerMsgID
	}
	if item.Sequence > 0 {
		return fmt.Sprintf("seq:%d", item.Sequence)
	}
	return "client:" + item.ClientMsgID
}

func currentMaxSeq(items []sdktypes.Message) int64 {
	var maxSeq int64
	for _, item := range items {
		if item.Sequence > maxSeq {
			maxSeq = item.Sequence
		}
	}
	return maxSeq
}

func resolveConversationID(message sdktypes.Message) string {
	switch message.ChatType {
	case 2:
		if message.GroupID != "" {
			return "g:" + message.GroupID
		}
	case 1:
		if message.SenderID != "" && message.ReceiverID != "" {
			if message.SenderID <= message.ReceiverID {
				return "s:" + message.SenderID + ":" + message.ReceiverID
			}
			return "s:" + message.ReceiverID + ":" + message.SenderID
		}
	}
	return ""
}
