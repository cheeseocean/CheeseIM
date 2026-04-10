package ui

import (
	"context"
	"fmt"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/service"
	"github.com/cheeseim/cheesebox/internal/store"
	"github.com/cheeseim/cheesebox/internal/transport/tcpim"
)

type loginSuccessMsg struct {
	data   service.InitialData
	userID string
	token  string
}

type loginErrorMsg struct {
	err error
}

type appErrorMsg struct {
	err error
}

type historyLoadedMsg struct {
	conversationID string
	items          []domain.HistoryMessage
}

type sendMessageSuccessMsg struct {
	conversationID string
	item           domain.MessageItem
}

type addFriendSuccessMsg struct {
	friendUserID string
}

type RootModel struct {
	cfg      config.RuntimeConfig
	login    LoginModel
	app      AppModel
	auth     *service.AuthService
	roster   *service.RosterService
	chat     *service.ChatService
	contacts *service.ContactService
	appStore *store.AppStore
	token    string
}

func NewRootModel(cfg config.RuntimeConfig, auth *service.AuthService, roster *service.RosterService, chat *service.ChatService, contacts *service.ContactService) RootModel {
	appStore := store.New()
	return RootModel{
		cfg:      cfg,
		login:    NewLoginModel(),
		appStore: appStore,
		app:      NewAppModel(appStore, cfg),
		auth:     auth,
		roster:   roster,
		chat:     chat,
		contacts: contacts,
	}
}

func (m RootModel) Init() tea.Cmd {
	return m.login.Init()
}

func (m RootModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case LoginSubmittedMsg:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnecting)
		return m, m.loginCmd(m.login.Values())
	case loginSuccessMsg:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
		m.appStore.SetCurrentUserID(msg.userID)
		m.token = msg.token
		m.appStore.SetFriends(msg.data.Friends)
		m.appStore.SetGroups(msg.data.Groups)
		for _, conversation := range msg.data.Conversations {
			m.appStore.UpsertConversation(conversation)
		}
		return m, m.waitRealtimeEventCmd()
	case OpenConversationMsg:
		return m, m.openConversationCmd(msg.ConversationID)
	case SubmitInputMsg:
		return m, m.submitInputCmd(strings.TrimSpace(msg.Text))
	case historyLoadedMsg:
		m.appStore.SetActiveConversation(msg.conversationID)
		items := make([]domain.MessageItem, 0, len(msg.items))
		for _, item := range msg.items {
			items = append(items, domain.MessageItem{
				ID:       item.ServerMsgID,
				SenderID: item.SenderID,
				Content:  item.Content,
			})
		}
		m.appStore.SetMessages(msg.conversationID, items)
		summary := m.appStore.Conversations[msg.conversationID]
		summary.ConversationID = msg.conversationID
		summary.UnreadCount = 0
		m.appStore.UpsertConversation(summary)
		return m, nil
	case sendMessageSuccessMsg:
		m.appStore.AppendMessage(msg.conversationID, msg.item)
		m.touchConversation(msg.conversationID, msg.item.Content)
		return m, nil
	case realtimeEventMsg:
		return m.handleRealtimeEvent(msg.event)
	case addFriendSuccessMsg:
		m.appStore.PushToast(domain.ToastKindSuccess, fmt.Sprintf("friend request sent to %s", msg.friendUserID))
		return m, nil
	case loginErrorMsg:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusError)
		m.appStore.PushToast(domain.ToastKindError, msg.err.Error())
		return m, nil
	case appErrorMsg:
		m.appStore.PushToast(domain.ToastKindError, msg.err.Error())
		return m, nil
	case ReconnectMsg:
		if m.token == "" {
			return m, nil
		}
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnecting)
		return m, m.reconnectCmd()
	}

	if !m.isAuthenticated() {
		updated, cmd := m.login.Update(msg)
		m.login = updated.(LoginModel)
		return m, cmd
	}

	updated, cmd := m.app.Update(msg)
	m.app = updated.(AppModel)
	return m, cmd
}

func (m RootModel) View() string {
	parts := []string{m.app.View()}
	if !m.isAuthenticated() {
		parts = append(parts, "", m.login.View())
	}
	if m.appStore.Toast.Message != "" {
		parts = append(parts, "", m.appStore.Toast.Message)
	}
	return strings.Join(parts, "\n")
}

func (m RootModel) loginCmd(values []string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		session, err := m.auth.Login(ctx, values[0], values[1], m.cfg.DeviceID, m.cfg.Platform, m.cfg.TCPAddr)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		data, err := m.roster.LoadInitialData(ctx, session.AccessToken)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		return loginSuccessMsg{data: data, userID: session.UserID, token: session.AccessToken}
	}
}

func (m RootModel) reconnectCmd() tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		session, err := m.auth.Reconnect(ctx, m.token, m.cfg.DeviceID, m.cfg.Platform, m.cfg.TCPAddr)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		data, err := m.roster.LoadInitialData(ctx, session.AccessToken)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		return loginSuccessMsg{data: data, userID: session.UserID, token: session.AccessToken}
	}
}

func (m RootModel) openConversationCmd(conversationID string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		items, err := m.chat.OpenConversation(ctx, m.token, conversationID, 50)
		if err != nil {
			return appErrorMsg{err: err}
		}
		return historyLoadedMsg{
			conversationID: conversationID,
			items:          items,
		}
	}
}

func (m RootModel) submitInputCmd(text string) tea.Cmd {
	if text == "" {
		return nil
	}
	if strings.HasPrefix(text, "/addfriend") {
		return m.addFriendCmd(text)
	}
	if m.appStore.ActiveConversation == "" {
		return func() tea.Msg { return appErrorMsg{err: fmt.Errorf("no active conversation")} }
	}
	return m.sendMessageCmd(text)
}

func (m RootModel) sendMessageCmd(text string) tea.Cmd {
	conversationID := m.appStore.ActiveConversation
	currentUserID := m.appStore.CurrentUserID
	return func() tea.Msg {
		item, err := m.chat.SendText(newRequestID(), conversationID, currentUserID, text)
		if err != nil {
			return appErrorMsg{err: err}
		}
		return sendMessageSuccessMsg{
			conversationID: conversationID,
			item:           item,
		}
	}
}

func (m RootModel) addFriendCmd(text string) tea.Cmd {
	fields := strings.Fields(text)
	if len(fields) < 2 {
		return func() tea.Msg { return loginErrorMsg{err: fmt.Errorf("usage: /addfriend <userId> [message]")} }
	}
	friendUserID := fields[1]
	message := ""
	if len(fields) > 2 {
		message = strings.Join(fields[2:], " ")
	}
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := m.contacts.AddFriend(ctx, m.token, friendUserID, message); err != nil {
			return appErrorMsg{err: err}
		}
		return addFriendSuccessMsg{friendUserID: friendUserID}
	}
}

func (m RootModel) waitRealtimeEventCmd() tea.Cmd {
	events := m.auth.Events()
	if events == nil {
		return nil
	}
	return func() tea.Msg {
		event, ok := <-events
		if !ok {
			return realtimeEventMsg{event: tcpim.Event{Kind: tcpim.EventDisconnect}}
		}
		return realtimeEventMsg{event: event}
	}
}

func (m RootModel) handleRealtimeEvent(event tcpim.Event) (tea.Model, tea.Cmd) {
	next := m.waitRealtimeEventCmd()
	switch event.Kind {
	case tcpim.EventMessage:
		conversationID, item, ok := m.chat.ResolveRealtimeEvent(event, m.appStore.CurrentUserID)
		if !ok {
			return m, next
		}
		m.appStore.AppendMessage(conversationID, item)
		m.touchConversation(conversationID, item.Content)
		if m.appStore.ActiveConversation != conversationID {
			summary := m.appStore.Conversations[conversationID]
			summary.ConversationID = conversationID
			summary.UnreadCount++
			m.appStore.UpsertConversation(summary)
		}
		return m, next
	case tcpim.EventDisconnect:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusDisconnected)
		m.appStore.PushToast(domain.ToastKindWarning, "tcp connection disconnected")
		return m, nil
	case tcpim.EventError:
		if event.Err != nil {
			m.appStore.PushToast(domain.ToastKindError, event.Err.Error())
		}
		return m, next
	default:
		return m, next
	}
}

func (m RootModel) touchConversation(conversationID, preview string) {
	summary := m.appStore.Conversations[conversationID]
	summary.ConversationID = conversationID
	if summary.Title == "" {
		summary.Title = conversationID
	}
	summary.LastMessagePreview = preview
	summary.LastMessageTime = time.Now().UnixMilli()
	if m.appStore.ActiveConversation == conversationID {
		summary.UnreadCount = 0
	}
	m.appStore.UpsertConversation(summary)
}

func (m RootModel) isAuthenticated() bool {
	return m.appStore.ConnectionStatus == domain.ConnectionStatusConnected && m.token != ""
}

func newRequestID() string {
	return fmt.Sprintf("c%015x", time.Now().UnixNano()&0x0fffffffffffffff)
}
