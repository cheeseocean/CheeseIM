package ui

import (
	"fmt"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
)

type focusArea int

const (
	focusNav focusArea = iota
	focusList
	focusInput
)

const (
	// panelBodyHeight 面板内容区默认高度，也用作测试 fallback
	panelBodyHeight  = 16
	chatMessageGroups = 5
	// debugPanelWidth 调试面板固定宽度
	debugPanelWidth = 55
)

type AppModel struct {
	store    *store.AppStore
	cfg      config.RuntimeConfig
	focus    focusArea
	showHelp bool
	selected int
	input    textinput.Model
	theme    ThemeName
	locale   LocaleName
	expanded bool
	width    int
	height   int
	// 调试日志面板引用，由 RootModel 设置
	debugLog *DebugLogModel
}

func NewAppModel(appStore *store.AppStore, cfg config.RuntimeConfig) AppModel {
	input := textinput.New()
	input.Prompt = "> "
	input.CharLimit = 1000
	input.Width = 48
	model := AppModel{
		store:    appStore,
		cfg:      cfg,
		focus:    focusNav,
		input:    input,
		theme:    defaultTheme().Name,
		locale:   defaultLocale(),
		expanded: false,
	}
	model.applyText()
	return model
}

func (m *AppModel) SetSize(w, h int) {
	m.width = w
	m.height = h
	m.updateInputWidth()
}

func (m *AppModel) SetExpanded(expanded bool) {
	m.expanded = expanded
	m.updateInputWidth()
}

// updateInputWidth 根据当前布局更新输入框宽度，确保填满聊天面板
func (m *AppModel) updateInputWidth() {
	_, chatW, _, _ := m.computeLayout()
	promptLen := len(m.input.Prompt) // "> " = 2
	w := chatW - promptLen
	if w < 10 {
		w = 10
	}
	m.input.Width = w
}

func (m AppModel) Init() tea.Cmd {
	return nil
}

func (m AppModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		// 全局快捷键：使用 ctrl 组合，不干扰文本输入
		switch msg.Type {
		case tea.KeyCtrlF:
			m.expanded = !m.expanded
			m.updateInputWidth()
			return m, nil
		case tea.KeyCtrlT:
			m.theme = nextTheme(m.theme)
			return m, nil
		case tea.KeyCtrlL:
			m.locale = nextLocale(m.locale)
			m.applyText()
			return m, nil
		}

		if m.focus == focusInput {
			switch msg.String() {
			case "enter":
				text := strings.TrimSpace(m.input.Value())
				if text == "" {
					return m, nil
				}
				m.input.SetValue("")
				return m, func() tea.Msg { return SubmitInputMsg{Text: text} }
			case "esc":
				m.focus = focusNav
				return m, func() tea.Msg { return InputChangedMsg{} }
			case "tab":
				m.focus = focusNav
				return m, func() tea.Msg { return InputChangedMsg{} }
			}
			before := m.input.Value()
			var cmd tea.Cmd
			m.input, cmd = m.input.Update(msg)
			if m.input.Value() != before {
				value := m.input.Value()
				return m, tea.Batch(cmd, func() tea.Msg { return InputChangedMsg{Text: value} })
			}
			return m, cmd
		}

		switch msg.String() {
		case "c":
			m.setActiveNav(domain.NavKeyChats)
		case "f":
			m.setActiveNav(domain.NavKeyFriends)
		case "g":
			m.setActiveNav(domain.NavKeyGroups)
		case "s":
			m.setActiveNav(domain.NavKeySettings)
		case "h", "left":
			if m.focus == focusNav {
				m.setActiveNav(nextNav(m.store.ActiveNav, -1))
			}
		case "l", "right":
			if m.focus == focusNav {
				m.setActiveNav(nextNav(m.store.ActiveNav, 1))
			}
		case "tab":
			m.focus = (m.focus + 1) % 3
			if m.focus == focusInput {
				m.input.Focus()
			} else {
				m.input.Blur()
			}
		case "j", "down":
			m.moveDown()
		case "k", "up":
			m.moveUp()
		case "enter":
			if m.focus == focusNav {
				m.focus = focusList
				return m, nil
			}
			if conversationID, ok := m.selectedConversationID(); ok {
				return m, func() tea.Msg { return OpenConversationMsg{ConversationID: conversationID} }
			}
		case "esc":
			m.focus = focusNav
			m.input.Blur()
		case "?":
			m.showHelp = !m.showHelp
		case "/":
			m.focus = focusInput
			m.input.Focus()
			m.input.SetValue("/")
		case "r":
			return m, func() tea.Msg { return ReconnectMsg{} }
		case "q":
			return m, tea.Quit
		}
	}
	return m, nil
}

// computeLayout 根据终端尺寸动态计算各面板宽高
// 返回：列表内容宽、聊天内容宽、tab面板内容宽（正常模式）、面板内容高
func (m AppModel) computeLayout() (listW, chatW, tabsW, bodyH int) {
	w := m.width
	if w <= 0 {
		w = 100
	}
	h := m.height
	if h <= 0 {
		h = 30
	}

	// 如果调试面板启用，预留其宽度（+2 是边框和间距）
	debugOffset := 0
	if m.debugLog != nil && m.debugLog.IsEnabled() {
		debugOffset = debugPanelWidth + 2
	}

	if m.expanded {
		// 固定行：tab(1) + hr(1) + hr底部(1) + 状态行(1) = 4
		bodyH = h - 4
		if bodyH < panelBodyHeight {
			bodyH = panelBodyHeight
		}
		// 扩展模式：无面板边框，直接使用内容宽度
		// listW = w/4, chatW = 剩余 - divider(3)
		listW = (w - debugOffset) / 4
		chatW = w - debugOffset - listW - 3
		tabsW = 0
		return
	}

	// 正常模式固定行：tab面板(3) + 面板上下边框(2) + 底部状态行(1) = 6
	bodyH = h - 6
	if bodyH < panelBodyHeight {
		bodyH = panelBodyHeight
	}
	// 两个面板各有 border(2)+padding(2)=4 字符开销
	// panelStyle 使用 RoundedBorder + Padding(0,1) = 左边框(1) + 左padding(1) + 右padding(1) + 右边框(1) = 4
	// 两个面板总共需要 8 字符边框开销，再加2列用于 lipgloss 内部对齐
	available := w - 8 - debugOffset
	if available < 30 {
		available = 30
	}
	listW = available / 4
	chatW = available - listW
	// tab 外宽 = list外宽 + chat外宽
	// tabsW + 4 = (listW+4) + (chatW+4) → tabsW = listW + chatW + 4
	tabsW = listW + chatW + 4
	return
}

// SetDebugLog 设置调试日志面板引用
func (m *AppModel) SetDebugLog(debugLog *DebugLogModel) {
	m.debugLog = debugLog
}

func (m AppModel) View() string {
	if m.expanded {
		return m.expandedView()
	}

	theme := themeByName(m.theme)
	listW, chatW, tabsW, bodyH := m.computeLayout()

	tabs := theme.panelStyle().Width(tabsW).Render(m.tabView(theme))
	list := theme.panelStyle().Width(listW).Height(bodyH).Render(m.listView(theme, bodyH))

	var chatContent string
	if m.showHelp {
		chatContent = helpView(m.locale, theme)
	} else {
		chatContent = m.chatView(theme, chatW, bodyH)
	}
	// 与 list 保持一致的渲染方式
	chat := theme.panelStyle().Width(chatW + 2).Height(bodyH).Render(chatContent)

	// 主面板内容：tabs + list/chat
	mainContent := strings.Join([]string{
		tabs,
		lipgloss.JoinHorizontal(lipgloss.Top, list, chat),
	}, "\n")

	// 如果调试日志启用，添加到右侧
	if m.debugLog != nil && m.debugLog.IsEnabled() {
		debugView := m.debugLog.ViewWithHeight(theme, bodyH+3) // +3 for tabs line
		if debugView != "" {
			mainContent = lipgloss.JoinHorizontal(lipgloss.Top, mainContent, debugView)
		}
	}

	// 底部状态栏：提示文字居左，连接状态居右
	statusText := theme.statusStyle().Render(T(m.locale, keyStatusLabel) + ": " + string(m.store.ConnectionStatus))
	hintText := theme.hintStyle().Render(m.hintView())
	totalW := (listW + 4) + (chatW + 4)
	gap := totalW - lipgloss.Width(hintText) - lipgloss.Width(statusText)
	if gap < 1 {
		gap = 1
	}
	bottomBar := hintText + strings.Repeat(" ", gap) + statusText

	return mainContent + "\n" + bottomBar
}

// expandedView 全屏无框布局，opencode 风格
func (m AppModel) expandedView() string {
	theme := themeByName(m.theme)
	listW, chatW, _, bodyH := m.computeLayout()

	w := m.width
	if w <= 0 {
		w = 100
	}

	hr := lipgloss.NewStyle().Foreground(theme.panelColor).Render(strings.Repeat("─", w))
	divider := " " + lipgloss.NewStyle().Foreground(theme.tabDivider).Render("│") + " "

	list := m.listView(theme, bodyH)
	chat := m.chatView(theme, chatW, bodyH)
	if m.showHelp {
		chat = helpView(m.locale, theme)
	}
	body := joinColumns(list, chat, listW, divider)

	// 扩展模式底部只显示连接状态，不显示提示行（提示行含扩展模式快捷键会混淆）
	statusText := theme.statusStyle().Render(T(m.locale, keyStatusLabel) + ": " + string(m.store.ConnectionStatus))
	bottomBar := lipgloss.PlaceHorizontal(w, lipgloss.Right, statusText)

	return strings.Join([]string{
		m.expandedTabView(theme),
		hr,
		body,
		hr,
		bottomBar,
	}, "\n")
}

// expandedTabView 扩展模式下无边框的 tab 栏
func (m AppModel) expandedTabView(theme Theme) string {
	items := []struct {
		key   domain.NavKey
		label localeKey
	}{
		{domain.NavKeyChats, keyTabChats},
		{domain.NavKeyFriends, keyTabFriends},
		{domain.NavKeyGroups, keyTabGroups},
		{domain.NavKeySettings, keyTabSettings},
	}
	parts := make([]string, 0, len(items))
	for _, item := range items {
		label := T(m.locale, item.label)
		if m.store.ActiveNav == item.key {
			parts = append(parts, theme.tabActiveStyle(m.focus == focusNav).Render("["+label+"]"))
		} else {
			parts = append(parts, theme.tabInactiveStyle(m.focus == focusNav).Render(" "+label+" "))
		}
	}
	return strings.Join(parts, theme.tabDividerStyle().Render("│"))
}

// joinColumns 将两列文本按固定左列宽逐行拼接，中间插入分隔符
func joinColumns(left, right string, leftW int, divider string) string {
	leftLines := strings.Split(left, "\n")
	rightLines := strings.Split(right, "\n")
	n := maxInt(len(leftLines), len(rightLines))
	rows := make([]string, n)
	for i := range rows {
		l, r := "", ""
		if i < len(leftLines) {
			l = leftLines[i]
		}
		if i < len(rightLines) {
			r = rightLines[i]
		}
		rows[i] = lipgloss.PlaceHorizontal(leftW, lipgloss.Left, l) + divider + r
	}
	return strings.Join(rows, "\n")
}

func (m AppModel) ThemeName() ThemeName {
	return m.theme
}

func (m *AppModel) SetTheme(name ThemeName) {
	m.theme = name
}

func (m *AppModel) SetLocale(name LocaleName) {
	m.locale = name
	m.applyText()
}

func (m *AppModel) applyText() {
	m.input.Placeholder = inputPlaceholderText(m.locale)
}

func (m AppModel) Focus() int {
	return int(m.focus)
}

func (m AppModel) ShowHelp() bool {
	return m.showHelp
}

func (m AppModel) selectedConversationID() (string, bool) {
	switch m.store.ActiveNav {
	case domain.NavKeyGroups:
		if len(m.store.Groups) == 0 {
			return "", false
		}
		if m.selected >= len(m.store.Groups) {
			m.selected = len(m.store.Groups) - 1
		}
		return "g:" + m.store.Groups[m.selected].GroupID, true
	case domain.NavKeyFriends:
		if len(m.store.Friends) == 0 {
			return "", false
		}
		if m.selected >= len(m.store.Friends) {
			m.selected = len(m.store.Friends) - 1
		}
		if m.store.CurrentUserID == "" {
			return "", false
		}
		return buildDirectConversationID(m.store.CurrentUserID, m.store.Friends[m.selected].UserID), true
	default:
		if len(m.store.ConversationOrder) == 0 {
			return "", false
		}
		if m.selected >= len(m.store.ConversationOrder) {
			m.selected = len(m.store.ConversationOrder) - 1
		}
		return m.store.ConversationOrder[m.selected], true
	}
}

func buildDirectConversationID(currentUserID, friendUserID string) string {
	if currentUserID <= friendUserID {
		return "s:" + currentUserID + ":" + friendUserID
	}
	return "s:" + friendUserID + ":" + currentUserID
}

func (m AppModel) tabView(theme Theme) string {
	items := []struct {
		key   domain.NavKey
		label localeKey
	}{
		{key: domain.NavKeyChats, label: keyTabChats},
		{key: domain.NavKeyFriends, label: keyTabFriends},
		{key: domain.NavKeyGroups, label: keyTabGroups},
		{key: domain.NavKeySettings, label: keyTabSettings},
	}
	rendered := make([]string, 0, len(items))
	for _, item := range items {
		style := theme.tabInactiveStyle(m.focus == focusNav)
		if m.store.ActiveNav == item.key {
			style = theme.tabActiveStyle(m.focus == focusNav)
		}
		label := T(m.locale, item.label)
		if m.store.ActiveNav == item.key {
			label = "[" + label + "]"
		}
		rendered = append(rendered, style.Render(label))
	}
	divider := theme.tabDividerStyle().Render(" │ ")
	parts := make([]string, 0, len(rendered)*2)
	for i, item := range rendered {
		if i > 0 {
			parts = append(parts, divider)
		}
		parts = append(parts, item)
	}
	return lipgloss.JoinHorizontal(lipgloss.Top, parts...)
}

func (m AppModel) listView(theme Theme, height int) string {
	switch m.store.ActiveNav {
	case domain.NavKeyFriends:
		if len(m.store.Friends) == 0 {
			return padLines([]string{theme.sectionTitleStyle().Render(T(m.locale, keyTabFriends)), T(m.locale, keyListEmpty)}, height)
		}
		lines := []string{theme.sectionTitleStyle().Render(T(m.locale, keyTabFriends))}
		start := visibleOffset(m.selected, len(m.store.Friends), height-1)
		end := minInt(len(m.store.Friends), start+height-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			lines = append(lines, m.renderListItem(actualIndex, m.store.Friends[actualIndex].DisplayName, theme))
		}
		return padLines(lines, height)
	case domain.NavKeyGroups:
		if len(m.store.Groups) == 0 {
			return padLines([]string{theme.sectionTitleStyle().Render(T(m.locale, keyTabGroups)), T(m.locale, keyListEmpty)}, height)
		}
		lines := []string{theme.sectionTitleStyle().Render(T(m.locale, keyTabGroups))}
		start := visibleOffset(m.selected, len(m.store.Groups), height-1)
		end := minInt(len(m.store.Groups), start+height-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			lines = append(lines, m.renderListItem(actualIndex, m.store.Groups[actualIndex].GroupName, theme))
		}
		return padLines(lines, height)
	case domain.NavKeySettings:
		return padLines([]string{
			theme.sectionTitleStyle().Render(T(m.locale, keySettingsTitle)),
			fmt.Sprintf("%s: %s", T(m.locale, keySettingsAPI), m.cfg.APIBaseURL),
			fmt.Sprintf("%s: %s", T(m.locale, keySettingsTCP), m.cfg.TCPAddr),
			fmt.Sprintf("%s: %s", T(m.locale, keySettingsDevice), m.cfg.DeviceID),
			fmt.Sprintf("%s: %s", T(m.locale, keySettingsPlatform), m.cfg.Platform),
		}, height)
	default:
		if len(m.store.ConversationOrder) == 0 {
			return padLines([]string{theme.sectionTitleStyle().Render(T(m.locale, keyTabChats)), T(m.locale, keyListEmpty)}, height)
		}
		lines := []string{theme.sectionTitleStyle().Render(T(m.locale, keyTabChats))}
		start := visibleOffset(m.selected, len(m.store.ConversationOrder), height-1)
		end := minInt(len(m.store.ConversationOrder), start+height-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			conversationID := m.store.ConversationOrder[actualIndex]
			lines = append(lines, m.renderConversationListItem(actualIndex, m.store.Conversations[conversationID], theme))
		}
		return padLines(lines, height)
	}
}

// chatView 渲染聊天区，innerW 为面板内容宽度（不含边框/padding）
func (m AppModel) chatView(theme Theme, innerW, height int) string {
	if innerW <= 0 {
		innerW = 56
	}
	contentW := innerW * 6 / 10
	if contentW < 20 {
		contentW = 20
	}

	sep := lipgloss.NewStyle().Foreground(theme.panelColor).Render(strings.Repeat("─", innerW))
	// layout: header(1) + msgSpace + sep(1) + input(1) = height，输入框固定在底部
	msgSpace := height - 3
	if msgSpace < 0 {
		msgSpace = 0
	}
	header := theme.sectionTitleStyle().Render(T(m.locale, keyChatTitle))
	if indicator, ok := m.store.ActiveTyping(m.store.ActiveConversation, time.Now().UnixMilli()); ok {
		header += "  " + theme.chatMetaStyle().Render(firstNonEmpty(indicator.SenderLabel, indicator.SenderID)+" is typing…")
	}

	if m.store.ActiveConversation == "" {
		return m.renderChatArea(header,
			[]string{theme.chatEmptyStateStyle().Render(T(m.locale, keyChatSelectPrompt))},
			msgSpace, sep, m.input.View(), innerW, contentW, theme)
	}

	messages := m.store.MessagesByConv[m.store.ActiveConversation]
	groups := buildChatGroups(messages)
	start := maxInt(0, len(groups)-chatMessageGroups)

	// 渲染消息组，考虑换行
	var allMsgLines []string
	if start > 0 {
		allMsgLines = append(allMsgLines, fmt.Sprintf(T(m.locale, keyOlderGroups), start))
	}
	for i := start; i < len(groups); i++ {
		if i > start {
			// 组间添加空行
			allMsgLines = append(allMsgLines, "")
		}
		groupLines := strings.Split(renderChatGroup(groups[i], theme, innerW, contentW), "\n")
		allMsgLines = append(allMsgLines, groupLines...)
	}

	chatContent := m.renderChatArea(header, allMsgLines, msgSpace, sep, m.input.View(), innerW, contentW, theme)

	// 使用 padLines 确保返回的内容恰好是 height 行
	return padLines(strings.Split(chatContent, "\n"), height)
}

// renderChatArea 将消息行填入固定高度区域，消息区顶部补空行，输入框始终在底部
// 返回恰好 (1 + msgSpace + 2) 行
func (m AppModel) renderChatArea(header string, msgLines []string, msgSpace int, sep, inputView string, innerW, contentW int, theme Theme) string {
	// 严格约束消息行数
	if len(msgLines) > msgSpace {
		msgLines = msgLines[len(msgLines)-msgSpace:]
	}
	
	// 构建行列表：header + msgLines + sep + input
	rows := make([]string, 0, 1+msgSpace+2)
	rows = append(rows, header)
	
	// 顶部补空行使消息靠底
	for i := 0; i < msgSpace-len(msgLines); i++ {
		rows = append(rows, "")
	}
	
	rows = append(rows, msgLines...)
	// 分隔线横跨整个聊天区域，输入框使用 contentW 与消息气泡保持一致
	rows = append(rows, sep)
	rows = append(rows, lipgloss.PlaceHorizontal(innerW, lipgloss.Left,
		lipgloss.NewStyle().Width(contentW).Render(inputView)))
	
	return strings.Join(rows, "\n")
}

type chatMessageGroup struct {
	label string
	self  bool
	items []string
}

func buildChatGroups(messages []domain.MessageItem) []chatMessageGroup {
	if len(messages) == 0 {
		return nil
	}
	groups := make([]chatMessageGroup, 0, len(messages))
	for _, item := range messages {
		content := item.Content
		if item.Self && item.DeliveryState != "" {
			content += "  [" + item.DeliveryState + "]"
		}
		label := firstNonEmpty(item.SenderLabel, item.SenderID)
		if item.Self {
			label = "me"
		}
		if len(groups) == 0 {
			groups = append(groups, chatMessageGroup{
				label: label,
				self:  item.Self,
				items: []string{content},
			})
			continue
		}
		last := &groups[len(groups)-1]
		if last.self == item.Self && last.label == label {
			last.items = append(last.items, content)
			continue
		}
		groups = append(groups, chatMessageGroup{
			label: label,
			self:  item.Self,
			items: []string{content},
		})
	}
	return groups
}

// renderChatGroup 渲染一组连续消息，innerW 控制对齐宽度，contentW 控制气泡最大宽度
func renderChatGroup(group chatMessageGroup, theme Theme, innerW, contentW int) string {
	align := lipgloss.Left
	contentStyle := theme.chatContentOther()
	if group.self {
		align = lipgloss.Right
		contentStyle = theme.chatContentSelf()
	}

	lines := make([]string, 0, 1+len(group.items)*2)

	// 渲染发送者标签
	meta := theme.chatMetaStyle().Render(group.label)
	lines = append(lines, lipgloss.PlaceHorizontal(innerW, align, meta))

	// 渲染每条消息，应用颜色和对齐
	for _, item := range group.items {
		// 先应用颜色和宽度限制，再对齐
		styled := contentStyle.MaxWidth(contentW).Render(item)
		// 将着色后的消息按行分割并逐行对齐
		for _, line := range strings.Split(styled, "\n") {
			lines = append(lines, lipgloss.PlaceHorizontal(innerW, align, line))
		}
	}

	return strings.Join(lines, "\n")
}

func (m AppModel) renderListItem(index int, label string, theme Theme) string {
	prefix := "  "
	if index == m.selected {
		prefix = "> "
	}
	line := prefix + label
	if m.focus == focusList && index == m.selected {
		line = theme.focusStyle().Render(line)
	}
	return line
}

func (m AppModel) renderConversationListItem(index int, item domain.ConversationSummary, theme Theme) string {
	line := m.renderListItem(index, item.Title, theme)
	if item.UnreadCount <= 0 {
		return line
	}
	badge := theme.unreadBadgeStyle().Render(fmt.Sprintf("%d", item.UnreadCount))
	return lipgloss.JoinHorizontal(lipgloss.Top, line, " ", badge)
}

func (m AppModel) hintView() string {
	switch m.focus {
	case focusNav:
		return T(m.locale, keyHintTabs)
	case focusList:
		return T(m.locale, keyHintList)
	default:
		return T(m.locale, keyHintInput)
	}
}

func inputPlaceholderText(locale LocaleName) string {
	if locale == LocaleZhCN {
		return "输入消息或 /addfriend <userId> [message]"
	}
	return "Type a message or /addfriend <userId> [message]"
}

func (m *AppModel) setActiveNav(nav domain.NavKey) {
	if m.store.ActiveNav == nav {
		return
	}
	m.store.SetActiveNav(nav)
	m.selected = 0
}

func (m *AppModel) moveDown() {
	switch m.focus {
	case focusNav:
		m.setActiveNav(nextNav(m.store.ActiveNav, 1))
	case focusList:
		last := m.listLength() - 1
		if last >= 0 && m.selected < last {
			m.selected++
		}
	}
}

func (m *AppModel) moveUp() {
	switch m.focus {
	case focusNav:
		m.setActiveNav(nextNav(m.store.ActiveNav, -1))
	case focusList:
		if m.selected > 0 {
			m.selected--
		}
	}
}

func (m AppModel) listLength() int {
	switch m.store.ActiveNav {
	case domain.NavKeyFriends:
		return len(m.store.Friends)
	case domain.NavKeyGroups:
		return len(m.store.Groups)
	case domain.NavKeySettings:
		return 0
	default:
		return len(m.store.ConversationOrder)
	}
}

func nextNav(current domain.NavKey, delta int) domain.NavKey {
	navs := []domain.NavKey{
		domain.NavKeyChats,
		domain.NavKeyFriends,
		domain.NavKeyGroups,
		domain.NavKeySettings,
	}
	index := 0
	for i, nav := range navs {
		if nav == current {
			index = i
			break
		}
	}
	index = (index + delta + len(navs)) % len(navs)
	return navs[index]
}

func padLines(lines []string, height int) string {
	if len(lines) > height {
		lines = lines[:height]
	}
	for len(lines) < height {
		lines = append(lines, "")
	}
	return strings.Join(lines, "\n")
}

func visibleOffset(selected, total, window int) int {
	if total <= window || window <= 0 {
		return 0
	}
	offset := selected - window + 1
	if offset < 0 {
		return 0
	}
	maxOffset := total - window
	if offset > maxOffset {
		return maxOffset
	}
	return offset
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}
