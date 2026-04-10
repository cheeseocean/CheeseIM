package ui

import (
	"fmt"
	"strings"

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
	panelBodyHeight = 16
	chatMessageRows = 10
)

type AppModel struct {
	store    *store.AppStore
	cfg      config.RuntimeConfig
	focus    focusArea
	showHelp bool
	selected int
	input    textinput.Model
}

func NewAppModel(store *store.AppStore, cfg config.RuntimeConfig) AppModel {
	input := textinput.New()
	input.Placeholder = "Type a message or /addfriend <userId> [message]"
	input.Prompt = "> "
	input.CharLimit = 1000
	input.Width = 48
	return AppModel{store: store, cfg: cfg, focus: focusNav, input: input}
}

func (m AppModel) Init() tea.Cmd {
	return nil
}

func (m AppModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
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
				return m, nil
			case "tab":
				m.focus = focusNav
				return m, nil
			}
			var cmd tea.Cmd
			m.input, cmd = m.input.Update(msg)
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

func (m AppModel) View() string {
	nav := panelStyle.Width(16).Height(panelBodyHeight).Render(m.navView())
	list := panelStyle.Width(26).Height(panelBodyHeight).Render(m.listView())
	chat := panelStyle.Width(58).Height(panelBodyHeight).Render(m.chatView())
	if m.showHelp {
		chat = panelStyle.Width(58).Height(panelBodyHeight).Render(helpView())
	}
	status := statusStyle.Render("Status: " + string(m.store.ConnectionStatus))
	hints := hintStyle.Render(m.hintView())
	return strings.Join([]string{
		titleStyle.Render("CheeseBox"),
		status,
		lipgloss.JoinHorizontal(lipgloss.Top, nav, list, chat),
		hints,
	}, "\n")
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
		return "c2:" + m.store.Groups[m.selected].GroupID, true
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

func (m AppModel) navView() string {
	lines := []string{"Navigation"}
	for _, item := range []struct {
		key   domain.NavKey
		label string
	}{
		{key: domain.NavKeyChats, label: "Chats"},
		{key: domain.NavKeyFriends, label: "Friends"},
		{key: domain.NavKeyGroups, label: "Groups"},
		{key: domain.NavKeySettings, label: "Settings"},
	} {
		prefix := "  "
		if m.store.ActiveNav == item.key {
			prefix = "> "
		}
		line := prefix + item.label
		if m.focus == focusNav && m.store.ActiveNav == item.key {
			line = focusStyle.Render(line)
		}
		lines = append(lines, line)
	}
	return padLines(lines, panelBodyHeight)
}

func (m AppModel) listView() string {
	switch m.store.ActiveNav {
	case domain.NavKeyFriends:
		if len(m.store.Friends) == 0 {
			return padLines([]string{"Friends", "(empty)"}, panelBodyHeight)
		}
		lines := []string{"Friends"}
		start := visibleOffset(m.selected, len(m.store.Friends), panelBodyHeight-1)
		end := minInt(len(m.store.Friends), start+panelBodyHeight-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			lines = append(lines, m.renderListItem(actualIndex, m.store.Friends[actualIndex].DisplayName))
		}
		return padLines(lines, panelBodyHeight)
	case domain.NavKeyGroups:
		if len(m.store.Groups) == 0 {
			return padLines([]string{"Groups", "(empty)"}, panelBodyHeight)
		}
		lines := []string{"Groups"}
		start := visibleOffset(m.selected, len(m.store.Groups), panelBodyHeight-1)
		end := minInt(len(m.store.Groups), start+panelBodyHeight-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			lines = append(lines, m.renderListItem(actualIndex, m.store.Groups[actualIndex].GroupName))
		}
		return padLines(lines, panelBodyHeight)
	case domain.NavKeySettings:
		return padLines([]string{
			"Settings",
			fmt.Sprintf("API: %s", m.cfg.APIBaseURL),
			fmt.Sprintf("TCP: %s", m.cfg.TCPAddr),
			fmt.Sprintf("Device: %s", m.cfg.DeviceID),
			fmt.Sprintf("Platform: %s", m.cfg.Platform),
		}, panelBodyHeight)
	default:
		if len(m.store.ConversationOrder) == 0 {
			return padLines([]string{"Chats", "(empty)"}, panelBodyHeight)
		}
		lines := []string{"Chats"}
		start := visibleOffset(m.selected, len(m.store.ConversationOrder), panelBodyHeight-1)
		end := minInt(len(m.store.ConversationOrder), start+panelBodyHeight-1)
		for actualIndex := start; actualIndex < end; actualIndex++ {
			conversationID := m.store.ConversationOrder[actualIndex]
			lines = append(lines, m.renderListItem(actualIndex, m.store.Conversations[conversationID].Title))
		}
		return padLines(lines, panelBodyHeight)
	}
}

func (m AppModel) chatView() string {
	inputLabel := "Input"
	if m.focus == focusInput {
		inputLabel = "Input (focused)"
	}
	if m.store.ActiveConversation == "" {
		return padLines([]string{
			"Chat",
			"Select a conversation",
			"",
			inputLabel,
			m.input.View(),
		}, panelBodyHeight)
	}
	lines := []string{"Chat"}
	messages := m.store.MessagesByConv[m.store.ActiveConversation]
	start := maxInt(0, len(messages)-chatMessageRows)
	if start > 0 {
		lines = append(lines, fmt.Sprintf("... %d older messages", start))
	}
	for _, item := range messages[start:] {
		prefix := item.SenderID
		if item.Self {
			prefix = "me"
		}
		lines = append(lines, fmt.Sprintf("%s: %s", prefix, item.Content))
	}
	lines = append(lines, "", inputLabel, m.input.View())
	return padLines(lines, panelBodyHeight)
}

func (m AppModel) renderListItem(index int, label string) string {
	prefix := "  "
	if index == m.selected {
		prefix = "> "
	}
	line := prefix + label
	if m.focus == focusList && index == m.selected {
		line = focusStyle.Render(line)
	}
	return line
}

func (m AppModel) hintView() string {
	switch m.focus {
	case focusNav:
		return "j/k move nav  enter open list  tab next  c/f/g/s quick switch  ? help  q quit"
	case focusList:
		return "j/k select item  enter open conversation  tab input  esc nav  / command  ? help"
	default:
		return "type message then enter  /addfriend <userId> [message]  esc nav  tab next"
	}
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
