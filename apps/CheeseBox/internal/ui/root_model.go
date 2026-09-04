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

type sessionSyncErrorMsg struct {
	generation uint64
	err        error
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

type revokeRequestedMsg struct {
	serverMsgID string
}

type conversationDeletedMsg struct {
	conversationID string
}

type conversationSyncSuccessMsg struct {
	generation uint64
	result     sdktypes.ConversationSyncResult
}

type controlEventSyncSuccessMsg struct {
	generation uint64
	result     sdktypes.ControlEventSyncResult
}

type friendStateLoadedMsg struct {
	friends  []sdktypes.Friend
	incoming []sdktypes.FriendRequest
	outgoing []sdktypes.FriendRequest
}

type friendActionSuccessMsg struct {
	action string
	userID string
}

var newUserPersister = func(userID string) (store.Persister, error) {
	return store.NewPersistedStoreForUser("", userID)
}

type IMClient interface {
	Login(ctx context.Context, userID, identityAssertion string) (sdktypes.BootstrapData, error)
	Reconnect(ctx context.Context) (sdktypes.BootstrapData, error)
	OpenConversation(ctx context.Context, conversationID string, limit int) ([]sdktypes.Message, error)
	PullMessages(ctx context.Context, ranges []sdktypes.SeqRange, limitPerConversation int64) ([]sdktypes.PulledConversationMessages, error)
	SendText(requestID, conversationID, text string) (sdktypes.Message, error)
	AddFriend(ctx context.Context, friendUserID, message string) error
	ListFriends(ctx context.Context) ([]sdktypes.Friend, error)
	ListIncomingFriendRequests(ctx context.Context) ([]sdktypes.FriendRequest, error)
	ListOutgoingFriendRequests(ctx context.Context) ([]sdktypes.FriendRequest, error)
	AcceptFriendRequest(ctx context.Context, friendUserID string) error
	RejectFriendRequest(ctx context.Context, friendUserID string) error
	CancelFriendRequest(ctx context.Context, friendUserID string) error
	DeleteConversation(ctx context.Context, conversationID string) error
	MarkRead(ctx context.Context, conversationID string, readSeq int64) error
	AckDelivered(conversationID string, deliveredSeq int64) error
	AckDeliveredWithOperationID(operationID, conversationID string, deliveredSeq int64) error
	RevokeMessage(conversationID, serverMsgID, reason string) error
	SendTyping(conversationID string, action sdktypes.TypingAction) error
	Events() <-chan sdktypes.Event
	CurrentUserID() string
	GetSyncedMaxSeq(conversationID string) int64
	GetServerMaxSeq(conversationID string) int64
	UpdateSyncedMaxSeq(conversationID string, seq int64)
	GetConversationCursor() sdktypes.ConversationSyncCursor
	UpdateConversationCursor(cursor sdktypes.ConversationSyncCursor)
	SyncConversations(ctx context.Context) (sdktypes.ConversationSyncResult, error)
	SyncControlEvents(ctx context.Context, cursor int64, limit int) (sdktypes.ControlEventSyncResult, error)
}

type RootModel struct {
	cfg                 config.RuntimeConfig
	login               LoginModel
	app                 AppModel
	client              IMClient
	syncer              *sync.Syncer
	appStore            *store.AppStore
	theme               ThemeName
	locale              LocaleName
	expanded            bool
	debugLog            *DebugLogModel
	width               int
	height              int
	typingActive        bool
	typingConversation  string
	lastTypingSentAt    time.Time
	conversationSynced  bool
	controlEventsSynced bool
	syncGeneration      uint64
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
	root.appStore.SetConnectionStatus(domain.ConnectionStatusLoggedOut)
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
		m.appStore.SetCurrentUserID(m.client.CurrentUserID())
		m.useUserPersister(m.client.CurrentUserID())
		m.client.UpdateConversationCursor(m.appStore.ConversationCursor)
		m.applyBootstrapData(msg.data)
		m.conversationSynced = false
		m.controlEventsSynced = false
		m.syncGeneration++
		m.appStore.SetConnectionStatus(domain.ConnectionStatusSyncing)
		commands := []tea.Cmd{m.syncConversationsCmd(), m.syncControlEventsCmd(m.appStore.ControlEventCursor), m.refreshFriendStateCmd(), m.waitRealtimeEventCmd()}
		for _, ack := range m.appStore.PendingDeliveryAcks() {
			commands = append(commands, m.retryDeliveryAckCmd(ack))
		}
		return m, tea.Batch(commands...)
	case conversationSyncSuccessMsg:
		if msg.generation != m.syncGeneration || m.appStore.ConnectionStatus != domain.ConnectionStatusSyncing {
			return m, nil
		}
		m.applyConversationSyncResult(msg.result)
		m.appStore.SetConversationCursor(msg.result.ConversationSyncCursor)
		m.conversationSynced = true
		m.markSessionReady()
		return m, nil
	case controlEventSyncSuccessMsg:
		if msg.generation != m.syncGeneration || m.appStore.ConnectionStatus != domain.ConnectionStatusSyncing {
			return m, nil
		}
		m.applyControlEvents(msg.result.Events)
		if err := m.appStore.SetControlEventCursor(msg.result.NextCursor); err != nil {
			m.appStore.SetConnectionStatus(domain.ConnectionStatusDisconnected)
			m.appStore.PushToast(domain.ToastKindError, "persist control event cursor: "+err.Error())
			return m, nil
		}
		if msg.result.HasMore {
			return m, m.syncControlEventsCmd(msg.result.NextCursor)
		}
		m.controlEventsSynced = true
		m.markSessionReady()
		return m, nil
	case OpenConversationMsg:
		stop := m.stopTypingCmd()
		m.typingActive = false
		m.typingConversation = ""
		return m, tea.Batch(stop, m.openConversationCmd(msg.ConversationID))
	case SubmitInputMsg:
		stop := m.stopTypingCmd()
		m.typingActive = false
		m.typingConversation = ""
		return m, tea.Batch(stop, m.submitInputCmd(strings.TrimSpace(msg.Text)))
	case InputChangedMsg:
		return m.handleInputChanged(msg.Text)
	case typingExpiredMsg:
		m.appStore.ExpireTyping(msg.conversationID, msg.senderID, msg.expiresAt)
		return m, nil
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
		return m, m.refreshFriendStateCmd()
	case friendStateLoadedMsg:
		m.applyFriendState(msg)
		return m, nil
	case friendActionSuccessMsg:
		m.appStore.PushToast(domain.ToastKindSuccess, fmt.Sprintf("friend request %s: %s", msg.action, msg.userID))
		return m, m.refreshFriendStateCmd()
	case revokeRequestedMsg:
		m.appStore.PushToast(domain.ToastKindSuccess, "revoke requested: "+msg.serverMsgID)
		return m, nil
	case conversationDeletedMsg:
		m.appStore.RemoveConversation(msg.conversationID)
		m.appStore.PushToast(domain.ToastKindSuccess, "conversation deleted: "+msg.conversationID)
		return m, nil
	case loginErrorMsg:
		if m.client.CurrentUserID() == "" {
			m.appStore.SetConnectionStatus(domain.ConnectionStatusLoggedOut)
		} else {
			m.appStore.SetConnectionStatus(domain.ConnectionStatusDisconnected)
		}
		m.appStore.PushToast(domain.ToastKindError, msg.err.Error())
		return m, nil
	case appErrorMsg:
		m.appStore.PushToast(domain.ToastKindError, msg.err.Error())
		return m, nil
	case sessionSyncErrorMsg:
		if msg.generation != m.syncGeneration {
			return m, nil
		}
		m.appStore.SetConnectionStatus(domain.ConnectionStatusDisconnected)
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
			return sessionSyncErrorMsg{generation: m.syncGeneration, err: err}
		}
		return conversationSyncSuccessMsg{generation: m.syncGeneration, result: result}
	}
}

func (m RootModel) syncControlEventsCmd(cursor int64) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		result, err := m.client.SyncControlEvents(ctx, cursor, 100)
		if err != nil {
			return sessionSyncErrorMsg{generation: m.syncGeneration, err: err}
		}
		if result.HasMore && result.NextCursor <= cursor {
			return sessionSyncErrorMsg{generation: m.syncGeneration, err: errors.New("control event cursor did not advance")}
		}
		return controlEventSyncSuccessMsg{generation: m.syncGeneration, result: result}
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
	if strings.HasPrefix(text, "/chat") {
		return m.openDirectChatCmd(text)
	}
	if strings.HasPrefix(text, "/revoke") {
		return m.revokeMessageCmd(text)
	}
	if text == "/delete" {
		return m.deleteConversationCmd()
	}
	if text == "/requests" {
		return m.refreshFriendStateCmd()
	}
	if strings.HasPrefix(text, "/accept") || strings.HasPrefix(text, "/reject") || strings.HasPrefix(text, "/cancel") {
		return m.handleFriendRequestCmd(text)
	}
	if m.appStore.ActiveConversation == "" {
		return func() tea.Msg { return appErrorMsg{err: errors.New(T(m.locale, keyToastNoConversation))} }
	}
	return m.sendMessageCmd(text)
}

func (m RootModel) deleteConversationCmd() tea.Cmd {
	conversationID := m.appStore.ActiveConversation
	if conversationID == "" {
		return func() tea.Msg { return appErrorMsg{err: errors.New(T(m.locale, keyToastNoConversation))} }
	}
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := m.client.DeleteConversation(ctx, conversationID); err != nil {
			return appErrorMsg{err: err}
		}
		return conversationDeletedMsg{conversationID: conversationID}
	}
}

func (m RootModel) handleFriendRequestCmd(text string) tea.Cmd {
	fields := strings.Fields(text)
	if len(fields) != 2 {
		return func() tea.Msg { return appErrorMsg{err: fmt.Errorf("usage: /accept|reject|cancel <userId>")} }
	}
	action := strings.TrimPrefix(fields[0], "/")
	userID := fields[1]
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		var err error
		switch action {
		case "accept":
			err = m.client.AcceptFriendRequest(ctx, userID)
		case "reject":
			err = m.client.RejectFriendRequest(ctx, userID)
		case "cancel":
			err = m.client.CancelFriendRequest(ctx, userID)
		default:
			err = fmt.Errorf("unsupported friend request action: %s", action)
		}
		if err != nil {
			return appErrorMsg{err: err}
		}
		return friendActionSuccessMsg{action: action, userID: userID}
	}
}

func (m RootModel) refreshFriendStateCmd() tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		friends, err := m.client.ListFriends(ctx)
		if err != nil {
			return appErrorMsg{err: err}
		}
		incoming, err := m.client.ListIncomingFriendRequests(ctx)
		if err != nil {
			return appErrorMsg{err: err}
		}
		outgoing, err := m.client.ListOutgoingFriendRequests(ctx)
		if err != nil {
			return appErrorMsg{err: err}
		}
		return friendStateLoadedMsg{friends: friends, incoming: incoming, outgoing: outgoing}
	}
}

func (m *RootModel) applyFriendState(state friendStateLoadedMsg) {
	friends := make([]domain.FriendSummary, 0, len(state.friends))
	for _, item := range state.friends {
		friends = append(friends, domain.FriendSummary{UserID: item.UserID, DisplayName: firstNonEmpty(item.DisplayName, item.UserID), AvatarSeed: item.AvatarURL})
	}
	incoming := make([]domain.FriendRequestSummary, 0, len(state.incoming))
	for _, item := range state.incoming {
		incoming = append(incoming, domain.FriendRequestSummary{UserID: item.FromUserID, RequestMessage: item.RequestMessage, Status: int(item.Status), CreateTime: item.CreateTime})
	}
	outgoing := make([]domain.FriendRequestSummary, 0, len(state.outgoing))
	for _, item := range state.outgoing {
		outgoing = append(outgoing, domain.FriendRequestSummary{UserID: item.ToUserID, RequestMessage: item.RequestMessage, Status: int(item.Status), CreateTime: item.CreateTime})
	}
	m.appStore.SetFriends(friends)
	m.appStore.SetFriendRequests(incoming, outgoing)
}

func (m RootModel) revokeMessageCmd(text string) tea.Cmd {
	if m.appStore.ActiveConversation == "" {
		return func() tea.Msg { return appErrorMsg{err: errors.New(T(m.locale, keyToastNoConversation))} }
	}
	fields := strings.Fields(text)
	if len(fields) < 2 {
		return func() tea.Msg { return appErrorMsg{err: fmt.Errorf("usage: /revoke <serverMsgId|last> [reason]")} }
	}
	serverMsgID := fields[1]
	if serverMsgID == "last" {
		serverMsgID = m.lastRevocableServerMsgID(m.appStore.ActiveConversation)
		if serverMsgID == "" {
			return func() tea.Msg {
				return appErrorMsg{err: errors.New("no revocable outgoing message in active conversation")}
			}
		}
	}
	reason := ""
	if len(fields) > 2 {
		reason = strings.Join(fields[2:], " ")
	}
	conversationID := m.appStore.ActiveConversation
	return func() tea.Msg {
		if err := m.client.RevokeMessage(conversationID, serverMsgID, reason); err != nil {
			return appErrorMsg{err: err}
		}
		return revokeRequestedMsg{serverMsgID: serverMsgID}
	}
}

func (m RootModel) lastRevocableServerMsgID(conversationID string) string {
	items := m.appStore.MessagesByConv[conversationID]
	for i := len(items) - 1; i >= 0; i-- {
		if items[i].Self && !items[i].Revoked && items[i].ServerMsgID != "" {
			return items[i].ServerMsgID
		}
	}
	return ""
}

func (m RootModel) openDirectChatCmd(text string) tea.Cmd {
	fields := strings.Fields(text)
	if len(fields) != 2 || fields[1] == "" || fields[1] == m.appStore.CurrentUserID {
		return func() tea.Msg { return appErrorMsg{err: fmt.Errorf("usage: /chat <otherUserId>")} }
	}
	conversationID := buildDirectConversationID(m.appStore.CurrentUserID, fields[1])
	return func() tea.Msg { return OpenConversationMsg{ConversationID: conversationID} }
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
		if event.Message.Sequence > 0 {
			return m, tea.Batch(next, m.ackDeliveredCmd(conversationID, event.Message.Sequence))
		}
		return m, next
	case sdktypes.EventKindAck:
		if event.SendAck != nil {
			m.appStore.UpdateSendAck(event.SendAck.ClientMsgID, event.SendAck.ServerMsgID)
		}
		return m, next
	case sdktypes.EventKindDeliveryUpdated:
		if event.Delivery != nil {
			m.appStore.UpdateDeliveredThrough(event.Delivery.ConversationID, event.Delivery.DeliveredSeq)
			if err := m.appStore.CompleteDeliveryAck(event.RequestID); err != nil {
				m.debugLog.AppendError("[STORE] complete delivery ack: " + err.Error())
			}
		}
		return m, next
	case sdktypes.EventKindReadUpdated:
		if event.Read != nil && event.Read.ReaderID != m.appStore.CurrentUserID {
			m.appStore.UpdateReadThrough(event.Read.ConversationID, event.Read.ReadSeq)
		}
		return m, next
	case sdktypes.EventKindRevokeUpdated:
		if event.Revoke != nil {
			m.appStore.ApplyRevoke(*event.Revoke)
		}
		return m, next
	case sdktypes.EventKindTypingUpdated:
		if event.Typing == nil || event.Typing.SenderID == m.appStore.CurrentUserID {
			return m, next
		}
		m.appStore.ApplyTyping(*event.Typing)
		if event.Typing.Action == sdktypes.TypingActionStart {
			return m, tea.Batch(next, typingExpiryCmd(*event.Typing))
		}
		return m, next
	case sdktypes.EventKindRosterUpdated:
		return m, tea.Batch(next, m.refreshFriendStateCmd())
	case sdktypes.EventKindForcedLogout:
		reason := T(m.locale, keyToastForcedLogout)
		if event.ForceLogout != nil && strings.TrimSpace(event.ForceLogout.Reason) != "" {
			reason += ": " + event.ForceLogout.Reason
		}
		m.resetSessionView()
		m.appStore.PushToast(domain.ToastKindError, reason)
		return m, nil
	case sdktypes.EventKindDisconnected:
		if !m.isAuthenticated() {
			return m, nil
		}
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

func (m *RootModel) resetSessionView() {
	m.appStore.ResetSession()
	m.login = NewLoginModel()
	m.login.SetTheme(m.theme)
	m.login.SetLocale(m.locale)
	m.app = NewAppModel(m.appStore, m.cfg)
	m.app.SetTheme(m.theme)
	m.app.SetLocale(m.locale)
	m.app.SetExpanded(m.expanded)
	m.app.SetDebugLog(m.debugLog)
	m.typingActive = false
	m.typingConversation = ""
	m.lastTypingSentAt = time.Time{}
	m.conversationSynced = false
	m.controlEventsSynced = false
	m.syncer = sync.NewSyncer(
		sync.NewMemoryStore(),
		sync.NewSDKPuller(m.client),
		m.client.GetServerMaxSeq,
		m.client.UpdateSyncedMaxSeq,
	)
}

func (m RootModel) handleInputChanged(text string) (tea.Model, tea.Cmd) {
	conversationID := m.appStore.ActiveConversation
	trimmed := strings.TrimSpace(text)
	if conversationID == "" || trimmed == "" || strings.HasPrefix(trimmed, "/") {
		stop := m.stopTypingCmd()
		m.typingActive = false
		m.typingConversation = ""
		return m, stop
	}
	now := time.Now()
	if m.typingActive && m.typingConversation == conversationID && now.Sub(m.lastTypingSentAt) < 2*time.Second {
		return m, nil
	}
	m.typingActive = true
	m.typingConversation = conversationID
	m.lastTypingSentAt = now
	return m, m.typingSignalCmd(conversationID, sdktypes.TypingActionStart)
}

func (m RootModel) stopTypingCmd() tea.Cmd {
	if !m.typingActive || m.typingConversation == "" {
		return nil
	}
	return m.typingSignalCmd(m.typingConversation, sdktypes.TypingActionStop)
}

func (m RootModel) typingSignalCmd(conversationID string, action sdktypes.TypingAction) tea.Cmd {
	return func() tea.Msg {
		_ = m.client.SendTyping(conversationID, action)
		return nil
	}
}

func typingExpiryCmd(update sdktypes.TypingUpdate) tea.Cmd {
	delay := time.Until(time.UnixMilli(update.ExpiresAt))
	if delay < 0 {
		delay = 0
	}
	return tea.Tick(delay, func(time.Time) tea.Msg {
		return typingExpiredMsg{conversationID: update.ConversationID, senderID: update.SenderID, expiresAt: update.ExpiresAt}
	})
}

func (m RootModel) ackDeliveredCmd(conversationID string, deliveredSeq int64) tea.Cmd {
	operationID := fmt.Sprintf("d%015x", time.Now().UnixNano()&0x0fffffffffffffff)
	ack := store.PendingDeliveryAck{OperationID: operationID, ConversationID: conversationID, DeliveredSeq: deliveredSeq}
	return func() tea.Msg {
		// 待确认位点与消息快照同步写入同一个文件；落盘失败时不得向服务端确认。
		if err := m.appStore.StageDeliveryAck(ack); err != nil {
			return appErrorMsg{err: fmt.Errorf("persist delivery ack: %w", err)}
		}
		if err := m.client.AckDeliveredWithOperationID(operationID, conversationID, deliveredSeq); err != nil {
			return appErrorMsg{err: err}
		}
		return nil
	}
}

func (m RootModel) retryDeliveryAckCmd(ack store.PendingDeliveryAck) tea.Cmd {
	return func() tea.Msg {
		if err := m.client.AckDeliveredWithOperationID(ack.OperationID, ack.ConversationID, ack.DeliveredSeq); err != nil {
			return appErrorMsg{err: err}
		}
		return nil
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

func (m *RootModel) applyControlEvents(events []sdktypes.ControlEvent) {
	for _, event := range events {
		switch event.Type {
		case sdktypes.ControlEventReadAdvanced:
			if event.Read != nil && event.Read.ReaderID != m.appStore.CurrentUserID {
				m.appStore.UpdateReadThrough(event.Read.ConversationID, event.Read.ReadSeq)
			}
		case sdktypes.ControlEventMessageRevoked:
			if event.Revoke != nil {
				m.appStore.ApplyRevoke(*event.Revoke)
			}
		case sdktypes.ControlEventDeliveryAdvanced:
			if event.Delivery != nil {
				m.appStore.UpdateDeliveredThrough(event.Delivery.ConversationID, event.Delivery.DeliveredSeq)
			}
		}
	}
}

func (m *RootModel) markSessionReady() {
	if m.appStore.ConnectionStatus == domain.ConnectionStatusSyncing && m.conversationSynced && m.controlEventsSynced {
		m.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
	}
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
		DeliveryState: func() string {
			if message.SenderID == currentUserID && message.Sequence == 0 {
				return string(sdktypes.MessageDeliverySending)
			}
			return ""
		}(),
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
