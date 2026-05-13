package store

import (
	"sort"
	"time"

	"github.com/cheeseim/cheesebox/internal/domain"
)

// Persister 持久化接口
type Persister interface {
	GetMessages(conversationID string) []MessageRecord
	AppendMessage(conversationID string, msg MessageRecord)
	SetMessages(conversationID string, msgs []MessageRecord)
	GetConversations() map[string]ConversationRecord
	UpsertConversation(conv ConversationRecord)
	ClearMessages(conversationID string)
	Clear()
}

type AppStore struct {
	ConnectionStatus   domain.ConnectionStatus
	CurrentUserID     string
	ActiveNav         domain.NavKey
	ActiveConversation string
	Friends           []domain.FriendSummary
	Groups            []domain.GroupSummary
	Conversations      map[string]domain.ConversationSummary
	ConversationOrder  []string
	MessagesByConv     map[string][]domain.MessageItem
	Toast              domain.Toast
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
	// 从持久化恢复会话数据
	store.loadFromPersister()
	return store
}

// loadFromPersister 从持久化存储加载数据
func (s *AppStore) loadFromPersister() {
	if s.persister == nil {
		return
	}
	convs := s.persister.GetConversations()
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

func (s *AppStore) SetMessages(conversationID string, items []domain.MessageItem) {
	s.MessagesByConv[conversationID] = append([]domain.MessageItem(nil), items...)
	// 持久化
	if s.persister != nil {
		records := make([]MessageRecord, len(items))
		for i, item := range items {
			records[i] = MessageRecord{
				ID:          item.ID,
				SenderID:    item.SenderID,
				SenderLabel: item.SenderLabel,
				Content:     item.Content,
				Self:        item.Self,
			}
		}
		s.persister.SetMessages(conversationID, records)
	}
}

func (s *AppStore) AppendMessage(conversationID string, item domain.MessageItem) {
	s.MessagesByConv[conversationID] = append(s.MessagesByConv[conversationID], item)
	// 持久化
	if s.persister != nil {
		s.persister.AppendMessage(conversationID, MessageRecord{
			ID:          item.ID,
			SenderID:    item.SenderID,
			SenderLabel: item.SenderLabel,
			Content:     item.Content,
			Self:        item.Self,
		})
	}
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
			ID:          rec.ID,
			SenderID:    rec.SenderID,
			SenderLabel: rec.SenderLabel,
			Content:     rec.Content,
			Self:        rec.Self,
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

func (s *AppStore) PushToast(kind domain.ToastKind, message string) {
	s.Toast = domain.Toast{
		Kind:    kind,
		Message: message,
		At:      time.Now(),
	}
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
