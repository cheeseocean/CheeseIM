package ui

import "github.com/charmbracelet/lipgloss"

var (
	titleStyle  = lipgloss.NewStyle().Bold(true)
	panelStyle  = lipgloss.NewStyle().Border(lipgloss.RoundedBorder()).Padding(0, 1)
	statusStyle = lipgloss.NewStyle().Foreground(lipgloss.Color("10"))
)
