package ui

import "github.com/cheeseim/cheesebox/internal/transport/tcpim"

type LoginSubmittedMsg struct{}

type ReconnectMsg struct{}

type OpenConversationMsg struct {
	ConversationID string
}

type SubmitInputMsg struct {
	Text string
}

type realtimeEventMsg struct {
	event tcpim.Event
}
