package ui

import (
	"context"
	"strings"
	"time"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/service"
	"github.com/cheeseim/cheesebox/internal/store"
)

type screen string

const (
	screenLogin screen = "login"
	screenApp   screen = "app"
)

type loginSuccessMsg struct {
	data service.InitialData
}

type loginErrorMsg struct {
	err error
}

type historyLoadedMsg struct {
	conversationID string
	items          []domain.HistoryMessage
}

type RootModel struct {
	cfg      config.RuntimeConfig
	screen   screen
	login    LoginModel
	app      AppModel
	auth     *service.AuthService
	roster   *service.RosterService
	chat     *service.ChatService
	appStore *store.AppStore
	token    string
}

func NewRootModel(cfg config.RuntimeConfig, auth *service.AuthService, roster *service.RosterService, chat *service.ChatService) RootModel {
	appStore := store.New()
	return RootModel{
		cfg:      cfg,
		screen:   screenLogin,
		login:    NewLoginModel(cfg),
		appStore: appStore,
		app:      NewAppModel(appStore, cfg),
		auth:     auth,
		roster:   roster,
		chat:     chat,
	}
}

func (m RootModel) Init() tea.Cmd {
	return m.login.Init()
}

func (m RootModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case LoginSubmittedMsg:
		values := m.login.Values()
		m.token = values[2]
		m.appStore.SetConnectionStatus("connecting")
		return m, m.loginCmd(values)
	case loginSuccessMsg:
		m.appStore.SetConnectionStatus("connected")
		m.appStore.SetFriends(msg.data.Friends)
		m.appStore.SetGroups(msg.data.Groups)
		for _, conversation := range msg.data.Conversations {
			m.appStore.UpsertConversation(conversation)
		}
		m.screen = screenApp
		return m, nil
	case OpenConversationMsg:
		return m, m.openConversationCmd(msg.ConversationID)
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
		return m, nil
	case loginErrorMsg:
		m.appStore.SetConnectionStatus("error")
		m.appStore.PushToast("error", msg.err.Error())
		return m, nil
	case ReconnectMsg:
		values := m.login.Values()
		m.appStore.SetConnectionStatus("connecting")
		return m, m.loginCmd(values)
	}

	switch m.screen {
	case screenApp:
		updated, cmd := m.app.Update(msg)
		m.app = updated.(AppModel)
		return m, cmd
	default:
		updated, cmd := m.login.Update(msg)
		m.login = updated.(LoginModel)
		return m, cmd
	}
}

func (m RootModel) View() string {
	if m.screen == screenApp {
		return m.app.View()
	}
	parts := []string{m.login.View()}
	if m.appStore.Toast.Message != "" {
		parts = append(parts, "", m.appStore.Toast.Message)
	}
	return strings.Join(parts, "\n")
}

func (m RootModel) loginCmd(values []string) tea.Cmd {
	return func() tea.Msg {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_, err := m.auth.Login(ctx, values[2], values[3], values[4], values[1])
		if err != nil {
			return loginErrorMsg{err: err}
		}
		data, err := m.roster.LoadInitialData(ctx, values[2])
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
		items, err := m.chat.OpenConversation(ctx, m.token, conversationID, 50)
		if err != nil {
			return loginErrorMsg{err: err}
		}
		return historyLoadedMsg{
			conversationID: conversationID,
			items:          items,
		}
	}
}
