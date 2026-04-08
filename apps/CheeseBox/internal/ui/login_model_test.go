package ui

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
)

func TestLoginModelDefaultsAndSubmit(t *testing.T) {
	model := NewLoginModel(config.RuntimeConfig{
		APIBaseURL: "http://127.0.0.1:8080",
		TCPAddr:    "127.0.0.1:9000",
		DeviceID:   "device-1",
		Platform:   "desktop",
	})

	values := model.Values()
	if values[0] != "http://127.0.0.1:8080" || values[1] != "127.0.0.1:9000" {
		t.Fatalf("unexpected values = %#v", values)
	}

	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	msg := cmd()
	if _, ok := msg.(LoginSubmittedMsg); !ok {
		t.Fatalf("msg = %#v, want LoginSubmittedMsg", msg)
	}
}

func TestLoginModelTabCyclesFocus(t *testing.T) {
	model := NewLoginModel(config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(LoginModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
}

func TestLoginModelViewContainsTitle(t *testing.T) {
	model := NewLoginModel(config.RuntimeConfig{})
	if !strings.Contains(model.View(), "CheeseBox Login") {
		t.Fatalf("view = %q", model.View())
	}
}
