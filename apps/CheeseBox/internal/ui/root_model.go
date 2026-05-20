package ui

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
	sdkclient "github.com/cheeseim/cheeseim-go-sdk/client"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
	"github.com/cheeseim/cheesebox/internal/sync"
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

type conversationSyncSuccessMsg struct {
	result sdktypes.ConversationSyncResult
}

var newUserPersister = func(userID string) (store.Persister, error) {
	return store.NewPersistedStoreForUser("", userID)
}

type IMClient interface {
	Login(ctx context.Context, userID, password string) (sdktypes.BootstrapData, error)
	Reconnect(ctx context.Context) (sdktypes.BootstrapData, error)
	OpenConversation(ctx context.Context, conversationID string, limit int) ([]sdktypes.Message, error)
	PullMessages(ctx context.Context, ranges []sdktypes.SeqRange, limitPerConversation int64) ([]sdktypes.PulledConversationMessages, error)
	SendText(requestID, conversationID, text string) (sdktypes.Message, error)
	AddFriend(ctx context.Context, friendUserID, message string) error
	MarkRead(ctx context.Context, conversationID string, readSeq int64) error
	Events() <-chan sdktypes.Event
	CurrentUserID() string
	GetSyncedMaxSeq(conversationID string) int64
	GetServerMaxSeq(conversationID string) int64
	UpdateSyncedMaxSeq(conversationID string, seq int64)
	GetConversationCursor() sdktypes.ConversationSyncCursor
	UpdateConversationCursor(cursor sdktypes.ConversationSyncCursor)
	SyncConversations(ctx context.Context) (sdktypes.ConversationSyncResult, error)
}

type RootModel struct {
	cfg      config.RuntimeConfig
	login    LoginModel
	app      AppModel
	client   IMClient
	syncer   *sync.Syncer
	appStore *store.AppStore
	theme    ThemeName
	locale   LocaleName
	expanded bool
	debugLog *DebugLogModel
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
		debugLog: NewDebugLogModel(),
	}
	root.login.SetTheme(root.theme)
	root.login.SetLocale(root.locale)
	root.app.SetTheme(root.theme)
	root.app.SetLocale(root.locale)
	root.app.SetExpanded(root.expanded)
	root.debugLog.SetEnabled(false)
	root.app.SetDebugLog(root.debugLog)
	root.syncer = sync.NewSyncer(
		sync.NewMemoryStore(),
		sync.NewSDKPuller(client),
		client.GetServerMaxSeq,
		client.UpdateSyncedMaxSeq,
	)
	return root
}

func (m RootModel) Init() tea.Cmd {
	return m.login.Init()
}

func (m RootModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	// 处理终端尺寸变化，立即传递给 app 和 debug log
	if sizeMsg, ok := msg.(tea.WindowSizeMsg); ok {
		m.width, m.height = sizeMsg.Width, sizeMsg.Height
		m.app.SetSize(sizeMsg.Width, sizeMsg.Height)
		// 计算调试面板的尺寸：右侧固定宽度
		debugWidth := debugPanelWidth
		debugHeight := sizeMsg.Height - 2 // 减去顶部 tab 和底部状态栏
		if debugWidth > sizeMsg.Width/3 {
			debugWidth = sizeMsg.Width / 3
		}
		if debugHeight < 15 {
			debugHeight = 15
		}
		m.debugLog.SetSize(debugWidth, debugHeight)
		return m, nil
	}
	// 全局快捷键
	if keyMsg, ok := msg.(tea.KeyMsg); ok {
		switch keyMsg.Type {
		case tea.KeyCtrlF:
			m.expanded = !m.expanded
			m.app.SetExpanded(m.expanded)
			return m, nil
		case tea.KeyCtrlD:
			m.debugLog.Toggle()
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
		m.useUserPersister(m.client.CurrentUserID())
		m.client.UpdateConversationCursor(m.appStore.ConversationCursor)
		m.applyBootstrapData(msg.data)
		return m, tea.Batch(m.syncConversationsCmd(), m.waitRealtimeEventCmd())
	case conversationSyncSuccessMsg:
		m.applyConversationSyncResult(msg.result)
		m.appStore.SetConversationCursor(msg.result.ConversationSyncCursor)
		return m, nil
	case OpenConversationMsg:
		return m, m.openConversationCmd(msg.ConversationID)
	case SubmitInputMsg:
		return m, m.submitInputCmd(strings.TrimSpace(msg.Text))
	case historyLoadedMsg:
		// 调试日志：加载历史消息
		var lastSenderID string
		var lastSeq int64
		if len(msg.items) > 0 {
			lastMsg := msg.items[len(msg.items)-1]
			lastSenderID = lastMsg.SenderID
			lastSeq = lastMsg.Sequence
		}
		m.debugLog.AppendHistory(msg.conversationID, len(msg.items), lastSenderID, lastSeq)

		m.appStore.SetActiveConversation(msg.conversationID)
		items := make([]domain.MessageItem, 0, len(msg.items))
		for _, item := range msg.items {
			items = append(items, toMessageItem(msg.conversationID, item, m.appStore.CurrentUserID))
		}
		m.appStore.SetMessages(msg.conversationID, items)
		summary := m.appStore.Conversations[msg.conversationID]
		summary.ConversationID = msg.conversationID
		summary.UnreadCount = 0
		m.appStore.UpsertConversation(summary)
		return m, nil
	case sendMessageSuccessMsg:
		m.debugLog.AppendSend(msg.conversationID, m.appStore.CurrentUserID, "", msg.item.Content)
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
		// 登录时也初始化调试面板尺寸
		if m.debugLog.width <= 0 {
			m.debugLog.SetSize(40, h)
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

func (m RootModel) syncConversationsCmd() tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		result, err := m.client.SyncConversations(ctx)
		if err != nil {
			return appErrorMsg{err: err}
		}
		return conversationSyncSuccessMsg{result: result}
	}
}

func (m RootModel) openConversationCmd(conversationID string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		// 从持久化存储恢复消息到同步器
		if m.syncer != nil {
			if records := m.appStore.GetPersistedMessages(conversationID); len(records) > 0 {
				m.debugLog.AppendInfo("[LOCAL] loaded from local store: " + conversationID)
			}
		}

		// 使用同步器拉取和合并消息；没有服务端 maxSeq 快照时降级到客户端历史接口。
		if m.syncer != nil && m.client.GetServerMaxSeq(conversationID) > 0 {
			messages, err := m.syncer.OpenConversation(ctx, conversationID, 50)
			if err != nil {
				return appErrorMsg{err: err}
			}

			// 标记已读
			var maxSeq int64
			for _, msg := range messages {
				if msg.Sequence > maxSeq {
					maxSeq = msg.Sequence
				}
			}
			if maxSeq > 0 {
				m.client.MarkRead(ctx, conversationID, maxSeq)
			}
			return historyLoadedMsg{
				conversationID: conversationID,
				items:          messages,
			}
		}

		// 降级：从客户端直接拉取
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
			m.client.MarkRead(ctx, conversationID, maxSeq)
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

	return func() tea.Msg {
		item, err := m.client.SendText(requestID, conversationID, text)
		if err != nil {
			m.debugLog.AppendError("[SEND ERROR] " + err.Error())
			return appErrorMsg{err: err}
		}

		return sendMessageSuccessMsg{
			conversationID: conversationID,
			item:           toMessageItem(conversationID, item, currentUserID),
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

		conversationID := event.ConversationID

		m.debugLog.AppendRecv(conversationID, event.Message.SenderID, event.Message.SenderName,
			string(event.Message.Content), event.Message.ClientMsgID, event.Message.ServerMsgID, event.Message.Sequence)

		item := toMessageItem(conversationID, *event.Message, m.appStore.CurrentUserID)
		messagesApplied := false

		// 使用同步器处理消息（合并、gap repair）
		if m.syncer != nil {
			ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
			defer cancel()
			result, err := m.syncer.HandleRealtime(ctx, *event.Message)
			if err != nil {
				m.debugLog.AppendError("[SYNC] " + err.Error())
			} else {
				if result.ConversationID != "" {
					conversationID = result.ConversationID
					item = toMessageItem(conversationID, *event.Message, m.appStore.CurrentUserID)
				}
				if len(result.Messages) > 0 {
					items := make([]domain.MessageItem, 0, len(result.Messages))
					for _, message := range result.Messages {
						items = append(items, toMessageItem(conversationID, message, m.appStore.CurrentUserID))
					}
					m.appStore.SetMessages(conversationID, items)
					messagesApplied = true
				}
				if result.Repaired {
					m.appStore.PushToast(domain.ToastKindInfo, T(m.locale, keyToastGapRepaired))
				}
			}
		}

		m.debugLog.AppendSelfCheck(event.Message.SenderID, m.appStore.CurrentUserID, item.Self)

		if !messagesApplied {
			m.appStore.AppendMessage(conversationID, item)
		}
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
			m.debugLog.AppendError("[ERR] " + event.Err.Error())
			m.appStore.PushToast(domain.ToastKindError, event.Err.Error())
		}
		return m, next
	default:
		return m, next
	}
}

func (m *RootModel) useUserPersister(userID string) {
	if userID == "" {
		return
	}
	persister, err := newUserPersister(userID)
	if err != nil {
		m.debugLog.AppendError("[STORE] " + err.Error())
		return
	}
	m.appStore.UsePersister(persister)
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
	m.debugLog.AppendConvTouch(conversationID, preview)
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

func (m *RootModel) applyConversationSyncResult(result sdktypes.ConversationSyncResult) {
	if result.Full {
		m.appStore.Conversations = make(map[string]domain.ConversationSummary)
		m.appStore.ConversationOrder = nil
	}
	for _, conversation := range result.Insert {
		m.appStore.UpsertConversation(toConversationSummary(conversation))
	}
	for _, conversation := range result.Update {
		m.appStore.UpsertConversation(toConversationSummary(conversation))
	}
	for _, conversationID := range result.Delete {
		m.appStore.RemoveConversation(conversationID)
	}
	m.appStore.SetActiveConversation("")
	m.appStore.SetActiveNav(domain.NavKeyChats)
	m.appStore.SetConversationCursor(result.ConversationSyncCursor)
}

func toMessageItem(conversationID string, message sdktypes.Message, currentUserID string) domain.MessageItem {
	id := firstNonEmpty(message.ServerMsgID, message.ClientMsgID)
	if id == "" && message.Sequence > 0 {
		id = fmt.Sprintf("seq:%d", message.Sequence)
	}
	return domain.MessageItem{
		ID:             id,
		ConversationID: conversationID,
		Sequence:       message.Sequence,
		ClientMsgID:    message.ClientMsgID,
		ServerMsgID:    message.ServerMsgID,
		SenderID:       message.SenderID,
		SenderLabel:    firstNonEmpty(message.SenderName, message.SenderID),
		Content:        string(message.Content),
		Self:           message.SenderID == currentUserID,
		SendTime:       message.SendTime,
		CreateTime:     message.CreateTime,
	}
}

func toConversationSummary(conversation sdktypes.Conversation) domain.ConversationSummary {
	return domain.ConversationSummary{
		ConversationID:     conversation.ConversationID,
		Title:              firstNonEmpty(conversation.Title, conversation.TargetID, conversation.ConversationID),
		Subtitle:           conversation.Subtitle,
		Kind:               mapConversationKind(conversation.Kind),
		LastMessagePreview: conversation.LastMessagePreview,
		LastMessageTime:    conversation.LastMessageTime,
		UnreadCount:        conversation.UnreadCount,
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
