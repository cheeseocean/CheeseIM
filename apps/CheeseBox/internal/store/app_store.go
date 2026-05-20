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
	ConnectionStatus   domain.ConnectionStatus
	CurrentUserID      string
	ActiveNav          domain.NavKey
	ActiveConversation string
	Friends            []domain.FriendSummary
	Groups             []domain.GroupSummary
	Conversations      map[string]domain.ConversationSummary
	ConversationOrder  []string
	MessagesByConv     map[string][]domain.MessageItem
	Toast              domain.Toast
	ConversationCursor sdktypes.ConversationSyncCursor
	persister          Persister
}

func New() *AppStore {
	return &AppStore{
		ConnectionStatus: domain.ConnectionStatusDisconnected,
		ActiveNav:        domain.NavKeyChats,
		Conversations:    make(map[string]domain.ConversationSummary),
		MessagesByConv:   make(map[string][]domain.MessageItem),
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
	s.Conversations = make(map[string]domain.ConversationSummary)
	s.ConversationOrder = nil
	s.MessagesByConv = make(map[string][]domain.MessageItem)
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
		})
	}
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
