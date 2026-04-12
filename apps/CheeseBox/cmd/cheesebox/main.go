package main

import (
	"log"

	tea "github.com/charmbracelet/bubbletea"
	sdkclient "github.com/cheeseim/cheeseim-go-sdk/client"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/ui"
)

func main() {
	cfg := config.LoadRuntimeConfig()
	imClient := sdkclient.New(sdkclient.Config{
		APIBaseURL: cfg.APIBaseURL,
		TCPAddr:    cfg.TCPAddr,
		DeviceID:   cfg.DeviceID,
		Platform:   cfg.Platform,
	})
	program := tea.NewProgram(ui.NewRootModel(cfg, imClient), tea.WithAltScreen())
	if _, err := program.Run(); err != nil {
		log.Fatal(err)
	}
}
