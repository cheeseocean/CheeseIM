package ui

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
)

func TestAppModelNavigationSwitching(t *testing.T) {
	appStore := store.New()
	model := NewAppModel(appStore)

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
}

func TestAppModelTabAndEscFocus(t *testing.T) {
	model := NewAppModel(store.New())
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
	model := NewAppModel(store.New())
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
	model := NewAppModel(appStore)
	if !strings.Contains(model.View(), "Status: connected") {
		t.Fatalf("view = %q", model.View())
	}
}
