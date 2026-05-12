package imsync

import (
	"context"
	"fmt"
	"sort"
	stdsync "sync"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

type PullAPI interface {
	PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limitPerConversation int) ([]types.PulledConversationMessages, error)
	AckReadSeq(ctx context.Context, accessToken, conversationID string, readSeq int64) error
}

type Service struct {
	api            PullAPI
	currentUserID  string
	mu             stdsync.RWMutex
	syncedMaxSeqs  map[string]int64
	readSnapshots  map[string]types.ReadSnapshot
	messagesByConv map[string][]types.Message
}

func NewService(api PullAPI, currentUserID string) *Service {
	return &Service{
		api:            api,
		currentUserID:  currentUserID,
		syncedMaxSeqs:  make(map[string]int64),
		readSnapshots:  make(map[string]types.ReadSnapshot),
		messagesByConv: make(map[string][]types.Message),
	}
}

func (s *Service) Reset(currentUserID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.currentUserID = currentUserID
	s.syncedMaxSeqs = make(map[string]int64)
	s.readSnapshots = make(map[string]types.ReadSnapshot)
	s.messagesByConv = make(map[string][]types.Message)
}

func (s *Service) Bootstrap(data types.BootstrapData) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.syncedMaxSeqs = make(map[string]int64, len(data.MaxSeqs))
	for conversationID, seq := range data.MaxSeqs {
		s.syncedMaxSeqs[conversationID] = seq
	}
	s.readSnapshots = make(map[string]types.ReadSnapshot, len(data.ReadSnapshots))
	for conversationID, snapshot := range data.ReadSnapshots {
		s.readSnapshots[conversationID] = snapshot
	}
}

func (s *Service) OpenConversation(ctx context.Context, accessToken, conversationID string, limit int) ([]types.Message, error) {
	if limit <= 0 {
		limit = 50
	}
	s.mu.RLock()
	serverMax := s.syncedMaxSeqs[conversationID]
	localMessages := append([]types.Message(nil), s.messagesByConv[conversationID]...)
	s.mu.RUnlock()

	if len(localMessages) == 0 && serverMax > 0 {
		beginSeq := serverMax - int64(limit) + 1
		if beginSeq < 1 {
			beginSeq = 1
		}
		pulled, err := s.pull(ctx, accessToken, types.SeqRange{
			ConversationID: conversationID,
			BeginSeq:       beginSeq,
			EndSeq:         serverMax,
		}, limit)
		if err != nil {
			return nil, err
		}
		return append([]types.Message(nil), pulled...), nil
	}
	return localMessages, nil
}

func (s *Service) HandleRealtimeMessage(ctx context.Context, accessToken string, message types.Message) (string, []types.Message, bool, error) {
	conversationID, err := resolveConversationID(message)
	if err != nil {
		return "", nil, false, err
	}

	s.mu.RLock()
	localMessages := append([]types.Message(nil), s.messagesByConv[conversationID]...)
	localMax := currentMaxSeq(localMessages)
	serverMax := s.syncedMaxSeqs[conversationID]
	s.mu.RUnlock()

	repaired := false
	if message.Sequence > 0 && localMax > 0 && message.Sequence > localMax+1 {
		pulled, err := s.pull(ctx, accessToken, types.SeqRange{
			ConversationID: conversationID,
			BeginSeq:       localMax + 1,
			EndSeq:         message.Sequence,
		}, int(message.Sequence-localMax))
		if err != nil {
			return "", nil, false, err
		}
		repaired = true
		localMessages = pulled
	} else {
		s.mu.Lock()
		s.messagesByConv[conversationID] = mergeMessages(s.messagesByConv[conversationID], []types.Message{message})
		s.messagesByConv[conversationID] = append([]types.Message(nil), s.messagesByConv[conversationID]...)
		if message.Sequence > serverMax {
			s.syncedMaxSeqs[conversationID] = message.Sequence
		}
		s.advanceSnapshotLocked(conversationID, message)
		localMessages = append([]types.Message(nil), s.messagesByConv[conversationID]...)
		s.mu.Unlock()
	}

	return conversationID, localMessages, repaired, nil
}

func (s *Service) MarkRead(ctx context.Context, accessToken, conversationID string, readSeq int64) (types.ReadSnapshot, error) {
	if err := s.api.AckReadSeq(ctx, accessToken, conversationID, readSeq); err != nil {
		return types.ReadSnapshot{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	snapshot := s.readSnapshots[conversationID]
	snapshot.ConversationID = conversationID
	if readSeq > snapshot.ReadSeq {
		snapshot.ReadSeq = readSeq
	}
	if snapshot.MaxSeq > snapshot.ReadSeq {
		snapshot.UnreadCount = snapshot.MaxSeq - snapshot.ReadSeq
	} else {
		snapshot.UnreadCount = 0
	}
	s.readSnapshots[conversationID] = snapshot
	return snapshot, nil
}

func (s *Service) Snapshot(conversationID string) types.ReadSnapshot {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.readSnapshots[conversationID]
}

func (s *Service) Messages(conversationID string) []types.Message {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return append([]types.Message(nil), s.messagesByConv[conversationID]...)
}

func (s *Service) pull(ctx context.Context, accessToken string, item types.SeqRange, limit int) ([]types.Message, error) {
	pulled, err := s.api.PullMessages(ctx, accessToken, []types.SeqRange{item}, limit)
	if err != nil {
		return nil, err
	}
	if len(pulled) == 0 {
		return nil, nil
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	conv := pulled[0]
	s.messagesByConv[item.ConversationID] = mergeMessages(s.messagesByConv[item.ConversationID], conv.Messages)
	if conv.EndSeq > s.syncedMaxSeqs[item.ConversationID] {
		s.syncedMaxSeqs[item.ConversationID] = conv.EndSeq
	}
	snapshot := s.readSnapshots[item.ConversationID]
	snapshot.ConversationID = item.ConversationID
	if conv.EndSeq > snapshot.MaxSeq {
		snapshot.MaxSeq = conv.EndSeq
	}
	if snapshot.MaxSeq > snapshot.ReadSeq {
		snapshot.UnreadCount = snapshot.MaxSeq - snapshot.ReadSeq
	} else {
		snapshot.UnreadCount = 0
	}
	s.readSnapshots[item.ConversationID] = snapshot
	return append([]types.Message(nil), s.messagesByConv[item.ConversationID]...), nil
}

func (s *Service) advanceSnapshotLocked(conversationID string, message types.Message) {
	snapshot := s.readSnapshots[conversationID]
	snapshot.ConversationID = conversationID
	if message.Sequence > snapshot.MaxSeq {
		snapshot.MaxSeq = message.Sequence
	}
	if message.SenderID != "" && message.SenderID != s.currentUserID && snapshot.MaxSeq > snapshot.ReadSeq {
		snapshot.UnreadCount = snapshot.MaxSeq - snapshot.ReadSeq
	} else if snapshot.MaxSeq <= snapshot.ReadSeq {
		snapshot.UnreadCount = 0
	}
	s.readSnapshots[conversationID] = snapshot
}

func mergeMessages(existing []types.Message, incoming []types.Message) []types.Message {
	merged := make([]types.Message, 0, len(existing)+len(incoming))
	seen := make(map[string]struct{}, len(existing)+len(incoming))
	add := func(item types.Message) {
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

func messageKey(item types.Message) string {
	switch {
	case item.Sequence > 0:
		return fmt.Sprintf("seq:%d", item.Sequence)
	case item.ServerMsgID != "":
		return "server:" + item.ServerMsgID
	default:
		return "client:" + item.ClientMsgID
	}
}

func currentMaxSeq(items []types.Message) int64 {
	var max int64
	for _, item := range items {
		if item.Sequence > max {
			max = item.Sequence
		}
	}
	return max
}

func resolveConversationID(message types.Message) (string, error) {
	switch message.ChatType {
	case 2:
		if message.GroupID == "" {
			return "", fmt.Errorf("group message missing group id")
		}
		return "c2:" + message.GroupID, nil
	case 1:
		if message.SenderID == "" || message.ReceiverID == "" {
			return "", fmt.Errorf("direct message missing sender or receiver")
		}
		if message.SenderID <= message.ReceiverID {
			return "s:" + message.SenderID + ":" + message.ReceiverID, nil
		}
		return "s:" + message.ReceiverID + ":" + message.SenderID, nil
	default:
		return "", fmt.Errorf("unsupported chat type: %d", message.ChatType)
	}
}
