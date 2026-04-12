package types

type EventKind string

const (
	EventKindConnected      EventKind = "connected"
	EventKindAck            EventKind = "ack"
	EventKindRealtime       EventKind = "realtime"
	EventKindSyncStarted    EventKind = "sync_started"
	EventKindSyncCompleted  EventKind = "sync_completed"
	EventKindGapRepaired    EventKind = "gap_repaired"
	EventKindReadUpdated    EventKind = "read_updated"
	EventKindDisconnected   EventKind = "disconnected"
	EventKindError          EventKind = "error"
)

type Event struct {
	Kind           EventKind
	RequestID      string
	UserID         string
	ConversationID string
	Message        *Message
	ReadSnapshot   *ReadSnapshot
	Err            error
}
