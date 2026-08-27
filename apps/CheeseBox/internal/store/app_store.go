package store

import (
	"fmt"
	"sort"
	"time"

	"github.com/cheeseim/cheesebox/internal/domain"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

// Persister 持久化接口
type Persister interface {
	GetMessages(conversationID string) []MessageRecord
	AppendMessage(conversationID string, msg MessageRecord)
	SetMessages(conversationID string, msgs []MessageRecord)
	GetConversations() map[string]ConversationRecord
	UpsertConversation(conv ConversationRecord)
	GetConversationCursor() sdktypes.ConversationSyncCursor
	SetConversationCursor(cursor sdktypes.ConversationSyncCursor)
	ClearMessages(conversationID string)
	Clear()
}

type AppStore struct {
	ConnectionStatus       domain.ConnectionStatus
	CurrentUserID          string
	ActiveNav              domain.NavKey
	ActiveConversation     string
	Friends                []domain.FriendSummary
	IncomingFriendRequests []domain.FriendRequestSummary
	OutgoingFriendRequests []domain.FriendRequestSummary
	Groups                 []domain.GroupSummary
	Conversations          map[string]domain.ConversationSummary
	ConversationOrder      []string
	MessagesByConv         map[string][]domain.MessageItem
	DeliveredSeqByConv     map[string]int64
	ReadSeqByConv          map[string]int64
	RevokesByServerID      map[string]domain.RevokeInfo
	TypingByConv           map[string]domain.TypingIndicator
	Toast                  domain.Toast
	ConversationCursor     sdktypes.ConversationSyncCursor
	persister              Persister
}

func New() *AppStore {
	return &AppStore{
		ConnectionStatus:   domain.ConnectionStatusDisconnected,
		ActiveNav:          domain.NavKeyChats,
		Conversations:      make(map[string]domain.ConversationSummary),
		MessagesByConv:     make(map[string][]domain.MessageItem),
		DeliveredSeqByConv: make(map[string]int64),
		ReadSeqByConv:      make(map[string]int64),
		RevokesByServerID:  make(map[string]domain.RevokeInfo),
		TypingByConv:       make(map[string]domain.TypingIndicator),
	}
}

// NewWithPersister 创建带持久化的 AppStore
func NewWithPersister(persister Persister) *AppStore {
	store := New()
	store.persister = persister
	return store
}

// NewWithLoadedPersister 创建带持久化的 AppStore，并立即恢复本地会话快照。
func NewWithLoadedPersister(persister Persister) *AppStore {
	store := NewWithPersister(persister)
	store.loadFromPersister()
	return store
}

// UsePersister 切换本地持久化命名空间，并恢复该命名空间下的会话快照。
func (s *AppStore) UsePersister(persister Persister) {
	s.persister = persister
	s.Friends = nil
	s.Groups = nil
	s.IncomingFriendRequests = nil
	s.OutgoingFriendRequests = nil
	s.Conversations = make(map[string]domain.ConversationSummary)
	s.ConversationOrder = nil
	s.MessagesByConv = make(map[string][]domain.MessageItem)
	s.DeliveredSeqByConv = make(map[string]int64)
	s.ReadSeqByConv = make(map[string]int64)
	s.RevokesByServerID = make(map[string]domain.RevokeInfo)
	s.TypingByConv = make(map[string]domain.TypingIndicator)
	s.ConversationCursor = sdktypes.ConversationSyncCursor{}
	s.loadFromPersister()
}

// loadFromPersister 从持久化存储加载数据
func (s *AppStore) loadFromPersister() {
	if s.persister == nil {
		return
	}
	convs := s.persister.GetConversations()
	s.ConversationCursor = s.persister.GetConversationCursor()
	for id, conv := range convs {
		s.Conversations[id] = domain.ConversationSummary{
			ConversationID:     conv.ConversationID,
			Title:              conv.Title,
			LastMessagePreview: conv.LastMessagePreview,
			LastMessageTime:    conv.LastMessageTime,
			UnreadCount:        conv.UnreadCount,
		}
	}
	s.rebuildOrder()
}

func (s *AppStore) SetConnectionStatus(status domain.ConnectionStatus) {
	s.ConnectionStatus = status
}

func (s *AppStore) SetCurrentUserID(userID string) {
	s.CurrentUserID = userID
}

func (s *AppStore) SetActiveNav(nav domain.NavKey) {
	s.ActiveNav = nav
}

func (s *AppStore) SetActiveConversation(conversationID string) {
	s.ActiveConversation = conversationID
}

func (s *AppStore) SetFriends(items []domain.FriendSummary) {
	s.Friends = append([]domain.FriendSummary(nil), items...)
}

func (s *AppStore) SetFriendRequests(incoming, outgoing []domain.FriendRequestSummary) {
	s.IncomingFriendRequests = append([]domain.FriendRequestSummary(nil), incoming...)
	s.OutgoingFriendRequests = append([]domain.FriendRequestSummary(nil), outgoing...)
}

func (s *AppStore) SetGroups(items []domain.GroupSummary) {
	s.Groups = append([]domain.GroupSummary(nil), items...)
}

func (s *AppStore) UpsertConversation(item domain.ConversationSummary) {
	s.Conversations[item.ConversationID] = item
	s.rebuildOrder()
	// 持久化
	if s.persister != nil {
		s.persister.UpsertConversation(ConversationRecord{
			ConversationID:     item.ConversationID,
			Title:              item.Title,
			LastMessagePreview: item.LastMessagePreview,
			LastMessageTime:    item.LastMessageTime,
			UnreadCount:        item.UnreadCount,
		})
	}
}

func (s *AppStore) RemoveConversation(conversationID string) {
	delete(s.Conversations, conversationID)
	delete(s.MessagesByConv, conversationID)
	s.rebuildOrder()
	if s.persister != nil {
		s.persister.ClearMessages(conversationID)
	}
}

func (s *AppStore) SetMessages(conversationID string, items []domain.MessageItem) {
	existing := s.MessagesByConv[conversationID]
	for i := range items {
		if items[i].DeliveryState == "" {
			items[i].DeliveryState = s.deliveryStateFor(items[i], existing)
		}
		if items[i].Self && items[i].Sequence > 0 && items[i].Sequence <= s.DeliveredSeqByConv[conversationID] {
			items[i].DeliveryState = string(sdktypes.MessageDeliveryDelivered)
		}
		if items[i].Self && items[i].Sequence > 0 && items[i].Sequence <= s.ReadSeqByConv[conversationID] {
			items[i].DeliveryState = string(sdktypes.MessageDeliveryRead)
		}
		s.applyRevokeToItem(&items[i])
	}
	s.MessagesByConv[conversationID] = append([]domain.MessageItem(nil), items...)
	// 持久化
	if s.persister != nil {
		records := make([]MessageRecord, len(items))
		for i, item := range items {
			records[i] = MessageRecord{
				ID:             item.ID,
				ConversationID: item.ConversationID,
				Sequence:       item.Sequence,
				ClientMsgID:    item.ClientMsgID,
				ServerMsgID:    item.ServerMsgID,
				SenderID:       item.SenderID,
				SenderLabel:    item.SenderLabel,
				Content:        item.Content,
				Self:           item.Self,
				SendTime:       item.SendTime,
				CreateTime:     item.CreateTime,
				DeliveryState:  item.DeliveryState,
				Revoked:        item.Revoked, RevokedBy: item.RevokedBy, RevokedAt: item.RevokedAt,
				MutationVersion: item.MutationVersion,
			}
		}
		s.persister.SetMessages(conversationID, records)
	}
}

func (s *AppStore) AppendMessage(conversationID string, item domain.MessageItem) {
	key := messageIdentity(item)
	if key != "" {
		for _, existing := range s.MessagesByConv[conversationID] {
			if messageIdentity(existing) == key {
				return
			}
		}
	}
	if item.ConversationID == "" {
		item.ConversationID = conversationID
	}
	s.applyRevokeToItem(&item)
	s.MessagesByConv[conversationID] = append(s.MessagesByConv[conversationID], item)
	// 持久化
	if s.persister != nil {
		s.persister.AppendMessage(conversationID, MessageRecord{
			ID:             item.ID,
			ConversationID: item.ConversationID,
			Sequence:       item.Sequence,
			ClientMsgID:    item.ClientMsgID,
			ServerMsgID:    item.ServerMsgID,
			SenderID:       item.SenderID,
			SenderLabel:    item.SenderLabel,
			Content:        item.Content,
			Self:           item.Self,
			SendTime:       item.SendTime,
			CreateTime:     item.CreateTime,
			DeliveryState:  item.DeliveryState,
			Revoked:        item.Revoked, RevokedBy: item.RevokedBy, RevokedAt: item.RevokedAt,
			MutationVersion: item.MutationVersion,
		})
	}
}

func (s *AppStore) UpdateSendAck(clientMsgID, serverMsgID string) {
	for conversationID, items := range s.MessagesByConv {
		for i := range items {
			if items[i].ClientMsgID != clientMsgID {
				continue
			}
			items[i].ServerMsgID = serverMsgID
			items[i].DeliveryState = string(sdktypes.MessageDeliveryBrokerAccepted)
			for j := range items {
				if j == i || items[j].ServerMsgID != serverMsgID {
					continue
				}
				items[i].Sequence = items[j].Sequence
				items[i].CreateTime = items[j].CreateTime
				items = append(items[:j], items[j+1:]...)
				break
			}
			s.SetMessages(conversationID, items)
			return
		}
	}
}

func (s *AppStore) UpdateDeliveredThrough(conversationID string, deliveredSeq int64) {
	if deliveredSeq > s.DeliveredSeqByConv[conversationID] {
		s.DeliveredSeqByConv[conversationID] = deliveredSeq
	}
	items := s.MessagesByConv[conversationID]
	changed := false
	for i := range items {
		if items[i].Self && items[i].Sequence > 0 && items[i].Sequence <= deliveredSeq &&
			items[i].DeliveryState != string(sdktypes.MessageDeliveryRead) {
			items[i].DeliveryState = string(sdktypes.MessageDeliveryDelivered)
			changed = true
		}
	}
	if changed {
		s.SetMessages(conversationID, items)
	}
}

func (s *AppStore) UpdateReadThrough(conversationID string, readSeq int64) {
	if readSeq > s.ReadSeqByConv[conversationID] {
		s.ReadSeqByConv[conversationID] = readSeq
	}
	items := s.MessagesByConv[conversationID]
	changed := false
	for i := range items {
		if items[i].Self && items[i].Sequence > 0 && items[i].Sequence <= readSeq {
			items[i].DeliveryState = string(sdktypes.MessageDeliveryRead)
			changed = true
		}
	}
	if changed {
		s.SetMessages(conversationID, items)
	}
}

func (s *AppStore) ApplyRevoke(update sdktypes.RevokeUpdate) {
	if update.ServerMsgID == "" {
		return
	}
	existing, ok := s.RevokesByServerID[update.ServerMsgID]
	if ok && existing.MutationVersion > update.MutationVersion {
		return
	}
	s.RevokesByServerID[update.ServerMsgID] = domain.RevokeInfo{
		ServerMsgID: update.ServerMsgID, OperatorUserID: update.OperatorUserID,
		OperatorName: update.OperatorName, RevokedAt: update.RevokedAt,
		MutationVersion: update.MutationVersion,
	}
	items := s.MessagesByConv[update.ConversationID]
	for i := range items {
		if items[i].ServerMsgID == update.ServerMsgID {
			s.applyRevokeToItem(&items[i])
			s.SetMessages(update.ConversationID, items)
			return
		}
	}
}

func (s *AppStore) ApplyTyping(update sdktypes.TypingUpdate) {
	if update.ConversationID == "" || update.SenderID == "" {
		return
	}
	if update.Action == sdktypes.TypingActionStop || update.ExpiresAt <= time.Now().UnixMilli() {
		if current, ok := s.TypingByConv[update.ConversationID]; ok && current.SenderID == update.SenderID {
			delete(s.TypingByConv, update.ConversationID)
		}
		return
	}
	s.TypingByConv[update.ConversationID] = domain.TypingIndicator{
		SenderID: update.SenderID, SenderLabel: update.SenderID, ExpiresAt: update.ExpiresAt,
	}
}

func (s *AppStore) ExpireTyping(conversationID, senderID string, expiresAt int64) {
	current, ok := s.TypingByConv[conversationID]
	if ok && current.SenderID == senderID && current.ExpiresAt == expiresAt {
		delete(s.TypingByConv, conversationID)
	}
}

func (s *AppStore) ActiveTyping(conversationID string, now int64) (domain.TypingIndicator, bool) {
	indicator, ok := s.TypingByConv[conversationID]
	if !ok || indicator.ExpiresAt <= now {
		return domain.TypingIndicator{}, false
	}
	return indicator, true
}

func (s *AppStore) applyRevokeToItem(item *domain.MessageItem) {
	if item == nil || item.ServerMsgID == "" {
		return
	}
	revoke, ok := s.RevokesByServerID[item.ServerMsgID]
	if !ok || revoke.MutationVersion < item.MutationVersion {
		return
	}
	item.Revoked = true
	item.RevokedBy = firstNonEmpty(revoke.OperatorName, revoke.OperatorUserID)
	item.RevokedAt = revoke.RevokedAt
	item.MutationVersion = revoke.MutationVersion
	item.Content = "[message revoked]"
}

func (s *AppStore) deliveryStateFor(item domain.MessageItem, existing []domain.MessageItem) string {
	for _, candidate := range existing {
		if (item.ServerMsgID != "" && candidate.ServerMsgID == item.ServerMsgID) ||
			(item.ClientMsgID != "" && candidate.ClientMsgID == item.ClientMsgID) {
			return candidate.DeliveryState
		}
	}
	return ""
}

func messageIdentity(item domain.MessageItem) string {
	if item.ServerMsgID != "" {
		return "server:" + item.ServerMsgID
	}
	if item.ClientMsgID != "" {
		return "client:" + item.ClientMsgID
	}
	if item.Sequence > 0 {
		return fmt.Sprintf("seq:%d", item.Sequence)
	}
	if item.ID != "" {
		return "id:" + item.ID
	}
	return ""
}

// LoadPersistedMessages 加载持久化的消息（用于首次打开会话时）
func (s *AppStore) LoadPersistedMessages(conversationID string) bool {
	if s.persister == nil {
		return false
	}
	records := s.persister.GetMessages(conversationID)
	if len(records) == 0 {
		return false
	}
	items := make([]domain.MessageItem, len(records))
	for i, rec := range records {
		items[i] = domain.MessageItem{
			ID:             rec.ID,
			ConversationID: firstNonEmpty(rec.ConversationID, conversationID),
			Sequence:       rec.Sequence,
			ClientMsgID:    rec.ClientMsgID,
			ServerMsgID:    rec.ServerMsgID,
			SenderID:       rec.SenderID,
			SenderLabel:    rec.SenderLabel,
			Content:        rec.Content,
			Self:           rec.Self,
			SendTime:       rec.SendTime,
			CreateTime:     rec.CreateTime,
			DeliveryState:  rec.DeliveryState,
			Revoked:        rec.Revoked, RevokedBy: rec.RevokedBy, RevokedAt: rec.RevokedAt,
			MutationVersion: rec.MutationVersion,
		}
	}
	s.MessagesByConv[conversationID] = items
	return true
}

// GetPersistedMessages 获取持久化的消息记录（用于同步器）
func (s *AppStore) GetPersistedMessages(conversationID string) []MessageRecord {
	if s.persister == nil {
		return nil
	}
	return s.persister.GetMessages(conversationID)
}

func (s *AppStore) SetConversationCursor(cursor sdktypes.ConversationSyncCursor) {
	s.ConversationCursor = cursor
	if s.persister != nil {
		s.persister.SetConversationCursor(cursor)
	}
}

func (s *AppStore) PushToast(kind domain.ToastKind, message string) {
	s.Toast = domain.Toast{
		Kind:    kind,
		Message: message,
		At:      time.Now(),
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

func (s *AppStore) rebuildOrder() {
	order := make([]string, 0, len(s.Conversations))
	for conversationID := range s.Conversations {
		order = append(order, conversationID)
	}
	sort.Slice(order, func(i, j int) bool {
		return s.Conversations[order[i]].LastMessageTime > s.Conversations[order[j]].LastMessageTime
	})
	s.ConversationOrder = order
}
