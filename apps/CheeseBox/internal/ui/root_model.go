package ui

import (
	"context"
	"errors"
	"fmt"
	"log"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	sdkclient "github.com/cheeseim/cheeseim-go-sdk/client"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
)

type loginSuccessMsg struct {
	data sdktypes.BootstrapData
}

type loginErrorMsg struct {
	err error
}

type appErrorMsg struct {
	err error
}

type historyLoadedMsg struct {
	conversationID string
	items          []sdktypes.Message
}

type sendMessageSuccessMsg struct {
	conversationID string
	item           domain.MessageItem
}

type addFriendSuccessMsg struct {
	friendUserID string
}

type IMClient interface {
	Login(ctx context.Context, userID, password string) (sdktypes.BootstrapData, error)
	Reconnect(ctx context.Context) (sdktypes.BootstrapData, error)
	OpenConversation(ctx context.Context, conversationID string, limit int) ([]sdktypes.Message, error)
	SendText(requestID, conversationID, text string) (sdktypes.Message, error)
	AddFriend(ctx context.Context, friendUserID, message string) error
	MarkRead(ctx context.Context, conversationID string, readSeq int64) (sdktypes.ReadSnapshot, error)
	Events() <-chan sdktypes.Event
	CurrentUserID() string
}

type RootModel struct {
	cfg      config.RuntimeConfig
	login    LoginModel
	app      AppModel
	client   IMClient
	appStore *store.AppStore
	theme    ThemeName
	locale   LocaleName
	expanded bool
	width    int
	height   int
}

func NewRootModel(cfg config.RuntimeConfig, client IMClient) RootModel {
	appStore := store.New()
	root := RootModel{
		cfg:      cfg,
		login:    NewLoginModel(),
		appStore: appStore,
		app:      NewAppModel(appStore, cfg),
		client:   client,
		theme:    defaultTheme().Name,
		locale:   defaultLocale(),
		expanded: false,
	}
	root.login.SetTheme(root.theme)
	root.login.SetLocale(root.locale)
	root.app.SetTheme(root.theme)
	root.app.SetLocale(root.locale)
	root.app.SetExpanded(root.expanded)
	return root
}

func (m RootModel) Init() tea.Cmd {
	return m.login.Init()
}

func (m RootModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	// 处理终端尺寸变化，立即传递给 app
	if sizeMsg, ok := msg.(tea.WindowSizeMsg); ok {
		m.width, m.height = sizeMsg.Width, sizeMsg.Height
		m.app.SetSize(sizeMsg.Width, sizeMsg.Height)
		return m, nil
	}
	// 全局快捷键：只使用不会被文本输入截获的 ctrl 组合键
	if keyMsg, ok := msg.(tea.KeyMsg); ok {
		switch keyMsg.String() {
		case "ctrl+f":
			m.expanded = !m.expanded
			m.app.SetExpanded(m.expanded)
			return m, nil
		}
	}
	switch msg := msg.(type) {
	case LoginSubmittedMsg:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnecting)
		return m, m.loginCmd(m.login.Values())
	case loginSuccessMsg:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
		m.appStore.SetCurrentUserID(m.client.CurrentUserID())
		m.applyBootstrapData(msg.data)
		return m, m.waitRealtimeEventCmd()
	case OpenConversationMsg:
		return m, m.openConversationCmd(msg.ConversationID)
	case SubmitInputMsg:
		return m, m.submitInputCmd(strings.TrimSpace(msg.Text))
	case historyLoadedMsg:
		// 调试日志：加载历史消息
		//log.Printf("[HISTORY] conversationID=%s, messageCount=%d", msg.conversationID, len(msg.items))
		//if len(msg.items) > 0 {
		//	lastMsg := msg.items[len(msg.items)-1]
		//	log.Printf("[HISTORY LAST] senderID=%s, content=%q, sequence=%d",
		//		lastMsg.SenderID, string(lastMsg.Content), lastMsg.Sequence)
		//}
		
		m.appStore.SetActiveConversation(msg.conversationID)
		items := make([]domain.MessageItem, 0, len(msg.items))
		for _, item := range msg.items {
			items = append(items, domain.MessageItem{
				ID:          firstNonEmpty(item.ServerMsgID, item.ClientMsgID),
				SenderID:    item.SenderID,
				SenderLabel: firstNonEmpty(item.SenderName, item.SenderID),
				Content:     string(item.Content),
				Self:        item.SenderID == m.appStore.CurrentUserID,
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
		if m.client.CurrentUserID() == "" {
			return m, nil
		}
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnecting)
		return m, m.reconnectCmd()
	}

	// t/l 等单字母快捷键全部交给子 model 处理，避免干扰文本输入
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
	// 登录态：居中弹框覆盖空白背景
	if !m.isAuthenticated() {
		w, h := m.width, m.height
		if w <= 0 {
			w = 80
		}
		if h <= 0 {
			h = 24
		}
		content := m.login.View()
		if m.appStore.Toast.Message != "" {
			content += "\n" + m.appStore.Toast.Message
		}
		return lipgloss.Place(w, h, lipgloss.Center, lipgloss.Center, content)
	}
	view := m.app.View()
	if m.appStore.Toast.Message != "" {
		view += "\n" + m.appStore.Toast.Message
	}
	return view
}

func (m RootModel) loginCmd(values []string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		data, err := m.client.Login(ctx, values[0], values[1])
		if err != nil {
			return loginErrorMsg{err: err}
		}
		return loginSuccessMsg{data: data}
	}
}

func (m RootModel) reconnectCmd() tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		data, err := m.client.Reconnect(ctx)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		return loginSuccessMsg{data: data}
	}
}

func (m RootModel) openConversationCmd(conversationID string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		items, err := m.client.OpenConversation(ctx, conversationID, 50)
		if err != nil {
			return appErrorMsg{err: err}
		}
		var maxSeq int64
		for _, item := range items {
			if item.Sequence > maxSeq {
				maxSeq = item.Sequence
			}
		}
		if maxSeq > 0 {
			if _, err := m.client.MarkRead(ctx, conversationID, maxSeq); err != nil {
				return appErrorMsg{err: err}
			}
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
		return func() tea.Msg { return appErrorMsg{err: errors.New(T(m.locale, keyToastNoConversation))} }
	}
	return m.sendMessageCmd(text)
}

func (m RootModel) sendMessageCmd(text string) tea.Cmd {
	conversationID := m.appStore.ActiveConversation
	currentUserID := m.appStore.CurrentUserID
	requestID := newRequestID()
	
	// 调试日志：发送消息
	//log.Printf("[SEND] conversationID=%s, userID=%s, requestID=%s, content=%q",
	//	conversationID, currentUserID, requestID, text)
	
	return func() tea.Msg {
		item, err := m.client.SendText(requestID, conversationID, text)
		if err != nil {
			log.Printf("[SEND ERROR] %v", err)
			return appErrorMsg{err: err}
		}
		// 调试日志：发送成功，SDK返回
		//log.Printf("[SEND SUCCESS] serverMsgID=%s, clientMsgID=%s, returnedContent=%q",
		//	item.ServerMsgID, item.ClientMsgID, string(item.Content))
		
		return sendMessageSuccessMsg{
			conversationID: conversationID,
			item: domain.MessageItem{
				ID:          firstNonEmpty(item.ServerMsgID, item.ClientMsgID),
				SenderID:    currentUserID,
				SenderLabel: "me",
				Content:     string(item.Content),
				Self:        true,
			},
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
		if err := m.client.AddFriend(ctx, friendUserID, message); err != nil {
			return appErrorMsg{err: err}
		}
		return addFriendSuccessMsg{friendUserID: friendUserID}
	}
}

func (m RootModel) waitRealtimeEventCmd() tea.Cmd {
	events := m.client.Events()
	if events == nil {
		return nil
	}
	return func() tea.Msg {
		event, ok := <-events
		if !ok {
			return realtimeEventMsg{event: sdktypes.Event{Kind: sdktypes.EventKindDisconnected}}
		}
		return realtimeEventMsg{event: event}
	}
}

func (m RootModel) handleRealtimeEvent(event sdktypes.Event) (tea.Model, tea.Cmd) {
	next := m.waitRealtimeEventCmd()
	switch event.Kind {
	case sdktypes.EventKindRealtime:
		if event.Message == nil {
			return m, next
		}
		
		// 调试日志：收到实时消息
		//log.Printf("[RECV] kind=realtime, conversationID=%s, senderID=%s, senderName=%s, content=%q, clientMsgID=%s, serverMsgID=%s",
		//	event.ConversationID,
		//	event.Message.SenderID,
		//	event.Message.SenderName,
		//	string(event.Message.Content),
		//	event.Message.ClientMsgID,
		//	event.Message.ServerMsgID)
		
		conversationID := event.ConversationID
		item := domain.MessageItem{
			ID:          firstNonEmpty(event.Message.ServerMsgID, event.Message.ClientMsgID),
			SenderID:    event.Message.SenderID,
			SenderLabel: firstNonEmpty(event.Message.SenderName, event.Message.SenderID),
			Content:     string(event.Message.Content),
			Self:        event.Message.SenderID == m.appStore.CurrentUserID,
		}
		
		// 调试日志：Self 判断结果
		//log.Printf("[RECV SELF CHECK] senderID=%s, currentUserID=%s, self=%v",
		//	event.Message.SenderID, m.appStore.CurrentUserID, item.Self)
		
		m.appStore.AppendMessage(conversationID, item)
		m.touchConversation(conversationID, item.Content)
		if m.appStore.ActiveConversation != conversationID {
			summary := m.appStore.Conversations[conversationID]
			summary.ConversationID = conversationID
			summary.UnreadCount++
			m.appStore.UpsertConversation(summary)
		}
		return m, next
	case sdktypes.EventKindDisconnected:
		m.appStore.SetConnectionStatus(domain.ConnectionStatusDisconnected)
		m.appStore.PushToast(domain.ToastKindWarning, T(m.locale, keyToastDisconnected))
		return m, nil
	case sdktypes.EventKindGapRepaired:
		m.appStore.PushToast(domain.ToastKindInfo, T(m.locale, keyToastGapRepaired))
		return m, next
	case sdktypes.EventKindError:
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
	
	// 调试日志：会话更新
	//log.Printf("[CONV TOUCH] conversationID=%s, preview=%q", conversationID, preview)
}

func (m RootModel) isAuthenticated() bool {
	return m.client.CurrentUserID() != ""
}

func newRequestID() string {
	return fmt.Sprintf("c%015x", time.Now().UnixNano()&0x0fffffffffffffff)
}

func (m *RootModel) applyBootstrapData(data sdktypes.BootstrapData) {
	friends := make([]domain.FriendSummary, 0, len(data.Friends))
	for _, item := range data.Friends {
		friends = append(friends, domain.FriendSummary{
			UserID:      item.UserID,
			DisplayName: firstNonEmpty(item.DisplayName, item.UserID),
			AvatarSeed:  item.AvatarURL,
		})
	}
	m.appStore.SetFriends(friends)
	groups := make([]domain.GroupSummary, 0, len(data.Groups))
	for _, item := range data.Groups {
		groups = append(groups, domain.GroupSummary{
			GroupID:   item.GroupID,
			GroupName: item.GroupName,
			FaceURL:   item.AvatarURL,
		})
	}
	m.appStore.SetGroups(groups)
	for _, conversation := range data.Conversations {
		m.appStore.UpsertConversation(domain.ConversationSummary{
			ConversationID:     conversation.ConversationID,
			Title:              firstNonEmpty(conversation.Title, conversation.TargetID, conversation.ConversationID),
			Subtitle:           conversation.Subtitle,
			Kind:               mapConversationKind(conversation.Kind),
			LastMessagePreview: conversation.LastMessagePreview,
			LastMessageTime:    conversation.LastMessageTime,
			UnreadCount:        conversation.UnreadCount,
		})
	}
}

func mapConversationKind(kind sdktypes.ConversationKind) domain.ConversationKind {
	if kind == sdktypes.ConversationKindGroup {
		return domain.ConversationKindGroup
	}
	return domain.ConversationKindDirect
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return ""
}

var _ IMClient = (*sdkclient.Client)(nil)
