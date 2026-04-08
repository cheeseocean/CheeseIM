package main

import (
	"log"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/ui"
)

func main() {
	cfg := config.LoadRuntimeConfig()
	program := tea.NewProgram(ui.NewLoginModel(cfg))
	if _, err := program.Run(); err != nil {
		log.Fatal(err)
	}
}
