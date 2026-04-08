package ui

type LoginSubmittedMsg struct{}

type ReconnectMsg struct{}

type OpenConversationMsg struct {
	ConversationID string
}
