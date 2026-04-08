package ui

import (
	"strings"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
)

type LoginModel struct {
	inputs []textinput.Model
	focus  int
}

func NewLoginModel(cfg config.RuntimeConfig) LoginModel {
	placeholders := []string{
		cfg.APIBaseURL,
		cfg.TCPAddr,
		"",
		cfg.DeviceID,
		cfg.Platform,
	}
	inputs := make([]textinput.Model, len(placeholders))
	for i, placeholder := range placeholders {
		input := textinput.New()
		input.Placeholder = placeholder
		if i == 0 {
			input.Focus()
		}
		inputs[i] = input
	}
	return LoginModel{inputs: inputs}
}

func (m LoginModel) Init() tea.Cmd {
	return textinput.Blink
}

func (m LoginModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "tab", "shift+tab":
			delta := 1
			if msg.String() == "shift+tab" {
				delta = -1
			}
			m.inputs[m.focus].Blur()
			m.focus = (m.focus + delta + len(m.inputs)) % len(m.inputs)
			m.inputs[m.focus].Focus()
			return m, nil
		case "enter":
			return m, func() tea.Msg { return LoginSubmittedMsg{} }
		}
	}
	cmds := make([]tea.Cmd, len(m.inputs))
	for i := range m.inputs {
		m.inputs[i], cmds[i] = m.inputs[i].Update(msg)
	}
	return m, tea.Batch(cmds...)
}

func (m LoginModel) View() string {
	lines := []string{
		titleStyle.Render("CheeseBox Login"),
		"",
	}
	for _, input := range m.inputs {
		lines = append(lines, input.View())
	}
	lines = append(lines, "", "Enter submit, Tab switch field")
	return strings.Join(lines, "\n")
}

func (m LoginModel) Values() []string {
	values := make([]string, len(m.inputs))
	for i := range m.inputs {
		if value := m.inputs[i].Value(); value != "" {
			values[i] = value
		} else {
			values[i] = m.inputs[i].Placeholder
		}
	}
	return values
}

func (m LoginModel) Focus() int {
	return m.focus
}
