package ui

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
)

func TestAppModelNavigationSwitching(t *testing.T) {
	appStore := store.New()
	model := NewAppModel(appStore, config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("f")})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeyFriends {
		t.Fatalf("nav = %q, want friends", appStore.ActiveNav)
	}

	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("g")})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeyGroups {
		t.Fatalf("nav = %q, want groups", appStore.ActiveNav)
	}

	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyDown})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeySettings {
		t.Fatalf("nav = %q, want settings", appStore.ActiveNav)
	}
}

func TestAppModelTabAndEscFocus(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(AppModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyEsc})
	model = updated.(AppModel)
	if model.Focus() != 0 {
		t.Fatalf("focus = %d, want 0", model.Focus())
	}
}

func TestAppModelHelpAndReconnect(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("?")})
	model = updated.(AppModel)
	if !model.ShowHelp() {
		t.Fatal("ShowHelp = false, want true")
	}

	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("r")})
	msg := cmd()
	if _, ok := msg.(ReconnectMsg); !ok {
		t.Fatalf("msg = %#v, want ReconnectMsg", msg)
	}
}

func TestAppModelViewShowsStatus(t *testing.T) {
	appStore := store.New()
	appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
	model := NewAppModel(appStore, config.RuntimeConfig{})
	if !strings.Contains(model.View(), "Status: connected") {
		t.Fatalf("view = %q", model.View())
	}
	if !strings.Contains(model.View(), "j/k move nav") {
		t.Fatalf("view = %q", model.View())
	}
}

func TestAppModelInputSubmit(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("/")})
	model = updated.(AppModel)
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("h")})
	model = updated.(AppModel)
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("i")})
	model = updated.(AppModel)
	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	msg := cmd()

	submit, ok := msg.(SubmitInputMsg)
	if !ok {
		t.Fatalf("msg = %#v, want SubmitInputMsg", msg)
	}
	if submit.Text != "/hi" {
		t.Fatalf("text = %q, want /hi", submit.Text)
	}
}

func TestAppModelEnterMovesFromNavToList(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	model = updated.(AppModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
}

func TestAppModelFriendsViewShowsSelection(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveNav(domain.NavKeyFriends)
	appStore.SetCurrentUserID("u100")
	appStore.SetFriends([]domain.FriendSummary{
		{UserID: "u100", DisplayName: "Alice"},
		{UserID: "u200", DisplayName: "Bob"},
	})
	model := NewAppModel(appStore, config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(AppModel)
	view := model.View()

	if !strings.Contains(view, "> Alice") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "enter open conversation") {
		t.Fatalf("view = %q", view)
	}
}

func TestAppModelSelectedFriendConversationIDUsesCurrentUser(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveNav(domain.NavKeyFriends)
	appStore.SetCurrentUserID("u200")
	appStore.SetFriends([]domain.FriendSummary{{UserID: "u100", DisplayName: "Alice"}})
	model := NewAppModel(appStore, config.RuntimeConfig{})
	model.focus = focusList

	conversationID, ok := model.selectedConversationID()
	if !ok {
		t.Fatal("selectedConversationID() = false, want true")
	}
	if conversationID != "s:u100:u200" {
		t.Fatalf("conversationID = %q, want s:u100:u200", conversationID)
	}
}

func TestAppModelChatViewOnlyShowsRecentMessages(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveConversation("s:u100:u200")
	items := make([]domain.MessageItem, 0, 12)
	for i := 0; i < 12; i++ {
		items = append(items, domain.MessageItem{
			ID:       "m",
			SenderID: "u100",
			Content:  "msg" + string(rune('A'+i)),
		})
	}
	appStore.SetMessages("s:u100:u200", items)
	model := NewAppModel(appStore, config.RuntimeConfig{})

	view := model.chatView()
	if strings.Contains(view, "msgA") || strings.Contains(view, "msgB") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "... 2 older messages") || !strings.Contains(view, "msgL") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "Input") || !strings.Contains(view, "> ") {
		t.Fatalf("view = %q", view)
	}
}
