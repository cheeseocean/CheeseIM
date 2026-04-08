package ui

import (
	"fmt"
	"strings"

	tea "github.com/charmbracelet/bubbletea"

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

type AppModel struct {
	store    *store.AppStore
	cfg      config.RuntimeConfig
	focus    focusArea
	showHelp bool
	selected int
}

func NewAppModel(store *store.AppStore, cfg config.RuntimeConfig) AppModel {
	return AppModel{store: store, cfg: cfg, focus: focusNav}
}

func (m AppModel) Init() tea.Cmd {
	return nil
}

func (m AppModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "c":
			m.store.SetActiveNav(domain.NavKeyChats)
		case "f":
			m.store.SetActiveNav(domain.NavKeyFriends)
		case "g":
			m.store.SetActiveNav(domain.NavKeyGroups)
		case "s":
			m.store.SetActiveNav(domain.NavKeySettings)
		case "tab":
			m.focus = (m.focus + 1) % 3
		case "j", "down":
			m.selected++
		case "k", "up":
			if m.selected > 0 {
				m.selected--
			}
		case "enter":
			if conversationID, ok := m.selectedConversationID(); ok {
				return m, func() tea.Msg { return OpenConversationMsg{ConversationID: conversationID} }
			}
		case "esc":
			m.focus = focusNav
		case "?":
			m.showHelp = !m.showHelp
		case "r":
			return m, func() tea.Msg { return ReconnectMsg{} }
		case "q":
			return m, tea.Quit
		}
	}
	return m, nil
}

func (m AppModel) View() string {
	nav := panelStyle.Render(strings.Join([]string{
		"Chats",
		"Friends",
		"Groups",
		"Settings",
	}, "\n"))
	list := panelStyle.Render(m.listView())
	chat := panelStyle.Render(m.chatView())
	if m.showHelp {
		chat = panelStyle.Render(helpView())
	}
	status := statusStyle.Render("Status: " + string(m.store.ConnectionStatus))
	return strings.Join([]string{
		titleStyle.Render("CheeseBox"),
		status,
		lipglossJoinHorizontal(nav, list, chat),
	}, "\n")
}

func (m AppModel) Focus() int {
	return int(m.focus)
}

func (m AppModel) ShowHelp() bool {
	return m.showHelp
}

func lipglossJoinHorizontal(parts ...string) string {
	return strings.Join(parts, " | ")
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
		return "c1:self:" + m.store.Friends[m.selected].UserID, true
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

func (m AppModel) listView() string {
	switch m.store.ActiveNav {
	case domain.NavKeyFriends:
		if len(m.store.Friends) == 0 {
			return "Friends\n(empty)"
		}
		lines := []string{"Friends"}
		for _, item := range m.store.Friends {
			lines = append(lines, item.DisplayName)
		}
		return strings.Join(lines, "\n")
	case domain.NavKeyGroups:
		if len(m.store.Groups) == 0 {
			return "Groups\n(empty)"
		}
		lines := []string{"Groups"}
		for _, item := range m.store.Groups {
			lines = append(lines, item.GroupName)
		}
		return strings.Join(lines, "\n")
	case domain.NavKeySettings:
		return strings.Join([]string{
			"Settings",
			fmt.Sprintf("API: %s", m.cfg.APIBaseURL),
			fmt.Sprintf("TCP: %s", m.cfg.TCPAddr),
			fmt.Sprintf("Device: %s", m.cfg.DeviceID),
			fmt.Sprintf("Platform: %s", m.cfg.Platform),
		}, "\n")
	default:
		if len(m.store.ConversationOrder) == 0 {
			return "Chats\n(empty)"
		}
		lines := []string{"Chats"}
		for _, conversationID := range m.store.ConversationOrder {
			lines = append(lines, m.store.Conversations[conversationID].Title)
		}
		return strings.Join(lines, "\n")
	}
}

func (m AppModel) chatView() string {
	if m.store.ActiveConversation == "" {
		return "Chat\nSelect a conversation"
	}
	lines := []string{"Chat"}
	for _, item := range m.store.MessagesByConv[m.store.ActiveConversation] {
		lines = append(lines, item.Content)
	}
	return strings.Join(lines, "\n")
}
