package ui

import "github.com/cheeseim/cheeseim-go-sdk/types"

type LoginSubmittedMsg struct{}

type ReconnectMsg struct{}

type OpenConversationMsg struct {
	ConversationID string
}

type SubmitInputMsg struct {
	Text string
}

type realtimeEventMsg struct {
	event types.Event
}
