package main

import (
	"log"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
)

type model struct {
	cfg config.RuntimeConfig
}

func (m model) Init() tea.Cmd {
	return nil
}

func (m model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	return m, nil
}

func (m model) View() string {
	return ""
}

func main() {
	cfg := config.LoadRuntimeConfig()
	program := tea.NewProgram(model{cfg: cfg})
	if _, err := program.Run(); err != nil {
		log.Fatal(err)
	}
}
