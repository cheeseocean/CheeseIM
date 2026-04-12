package ui

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"
)

func TestLoginModelDefaultsAndSubmit(t *testing.T) {
	model := NewLoginModel()

	values := model.Values()
	if values[0] != "" || values[1] != "" {
		t.Fatalf("unexpected values = %#v", values)
	}

	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	msg := cmd()
	if _, ok := msg.(LoginSubmittedMsg); !ok {
		t.Fatalf("msg = %#v, want LoginSubmittedMsg", msg)
	}
}

func TestLoginModelTabCyclesFocus(t *testing.T) {
	model := NewLoginModel()
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(LoginModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
}

func TestLoginModelViewContainsTitle(t *testing.T) {
	model := NewLoginModel()
	if !strings.Contains(model.View(), "登录") {
		t.Fatalf("view = %q", model.View())
	}
}
