package store

import (
	"sort"
	"time"

	"github.com/cheeseim/cheesebox/internal/domain"
)

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
}

func New() *AppStore {
	return &AppStore{
		ConnectionStatus: domain.ConnectionStatusDisconnected,
		ActiveNav:        domain.NavKeyChats,
		Conversations:    make(map[string]domain.ConversationSummary),
		MessagesByConv:   make(map[string][]domain.MessageItem),
	}
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
}

func (s *AppStore) SetMessages(conversationID string, items []domain.MessageItem) {
	s.MessagesByConv[conversationID] = append([]domain.MessageItem(nil), items...)
}

func (s *AppStore) AppendMessage(conversationID string, item domain.MessageItem) {
	s.MessagesByConv[conversationID] = append(s.MessagesByConv[conversationID], item)
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
