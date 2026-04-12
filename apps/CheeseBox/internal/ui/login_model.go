package ui

import (
	"strings"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
)

type LoginModel struct {
	inputs []textinput.Model
	focus  int
	theme  ThemeName
	locale LocaleName
}

func NewLoginModel() LoginModel {
	placeholders := []string{
		"User ID",
		"Password",
	}
	inputs := make([]textinput.Model, len(placeholders))
	for i, placeholder := range placeholders {
		input := textinput.New()
		input.Placeholder = placeholder
		if i == 1 {
			input.EchoMode = textinput.EchoPassword
			input.EchoCharacter = '*'
		}
		if i == 0 {
			input.Focus()
		}
		inputs[i] = input
	}
	model := LoginModel{
		inputs: inputs,
		theme:  defaultTheme().Name,
		locale: defaultLocale(),
	}
	model.applyText()
	return model
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
	theme := themeByName(m.theme)
	lines := []string{
		theme.titleStyle().Render(T(m.locale, keyLoginTitle)),
		"",
	}
	for _, input := range m.inputs {
		lines = append(lines, input.View())
	}
	lines = append(lines, "", theme.hintStyle().Render(T(m.locale, keyLoginHint)))
	return theme.panelStyle().Width(48).Render(strings.Join(lines, "\n"))
}

func (m LoginModel) Values() []string {
	values := make([]string, len(m.inputs))
	for i := range m.inputs {
		values[i] = strings.TrimSpace(m.inputs[i].Value())
	}
	return values
}

func (m LoginModel) Focus() int {
	return m.focus
}

func (m *LoginModel) SetTheme(name ThemeName) {
	m.theme = name
}

func (m *LoginModel) SetLocale(name LocaleName) {
	m.locale = name
	m.applyText()
}

func (m *LoginModel) applyText() {
	if len(m.inputs) > 0 {
		m.inputs[0].Placeholder = T(m.locale, keyLoginUserID)
	}
	if len(m.inputs) > 1 {
		m.inputs[1].Placeholder = T(m.locale, keyLoginPassword)
	}
}
