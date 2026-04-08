package main

import (
	"log"
	"time"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/service"
	"github.com/cheeseim/cheesebox/internal/transport/httpapi"
	"github.com/cheeseim/cheesebox/internal/transport/tcpim"
	"github.com/cheeseim/cheesebox/internal/ui"
)

func main() {
	cfg := config.LoadRuntimeConfig()
	httpClient := httpapi.New(cfg.APIBaseURL, 10*time.Second)
	tcpClient := tcpim.NewClient(nil, 30*time.Second)
	authService := service.NewAuthService(httpClient, tcpClient)
	rosterService := service.NewRosterService(httpClient)
	chatService := service.NewChatService(tcpClient, httpClient)
	program := tea.NewProgram(ui.NewRootModel(cfg, authService, rosterService, chatService))
	if _, err := program.Run(); err != nil {
		log.Fatal(err)
	}
}
