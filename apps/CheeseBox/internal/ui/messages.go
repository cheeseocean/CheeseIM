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

type InputChangedMsg struct {
	Text string
}

type typingExpiredMsg struct {
	conversationID string
	senderID       string
	expiresAt      int64
}

type realtimeEventMsg struct {
	event types.Event
}
