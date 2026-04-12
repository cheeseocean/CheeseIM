package types

import "testing"

func TestBootstrapDataShape(t *testing.T) {
	_ = BootstrapData{
		Friends:       []Friend{{UserID: "u100"}},
		Groups:        []Group{{GroupID: "g100"}},
		Conversations: []Conversation{{ConversationID: "s:u100:u200"}},
		MaxSeqs:       map[string]int64{"s:u100:u200": 10},
		ReadSnapshots: map[string]ReadSnapshot{"s:u100:u200": {ConversationID: "s:u100:u200"}},
	}
}

func TestEventShape(t *testing.T) {
	_ = Event{
		Kind:           EventKindRealtime,
		ConversationID: "s:u100:u200",
		Message:        &Message{ServerMsgID: "m1"},
	}
}
