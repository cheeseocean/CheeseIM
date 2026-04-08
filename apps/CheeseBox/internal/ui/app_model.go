package ui

import (
	"strings"

	tea "github.com/charmbracelet/bubbletea"

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
	focus    focusArea
	showHelp bool
}

func NewAppModel(store *store.AppStore) AppModel {
	return AppModel{store: store, focus: focusNav}
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
	list := panelStyle.Render("List")
	chat := panelStyle.Render("Chat")
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
