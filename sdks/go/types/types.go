package types

type ConversationKind int

const (
	ConversationKindDirect ConversationKind = iota
	ConversationKindGroup
)

type WsTicket struct {
	Ticket   string
	ExpireAt int64
	WSURL    string
}

type Friend struct {
	UserID      string
	DisplayName string
	AvatarURL   string
}

type Group struct {
	GroupID   string
	GroupName string
	AvatarURL string
}

type Conversation struct {
	OwnerUserID        string
	ConversationID     string
	ConversationType   int
	TargetID           string
	ReceiveOpt         int
	UnreadCount        int
	Pinned             bool
	AttachedInfo       string
	GroupAtType        int
	AutoCleanup        bool
	CleanupCycle       int64
	LatestCleanupTime  int64
	CreatedAt          int64
	UpdatedAt          int64
	Kind               ConversationKind
	Title              string
	Subtitle           string
	LastMessagePreview string
	LastMessageTime    int64
	Notification       bool
}

type Message struct {
	Sequence    int64
	ClientMsgID string
	ServerMsgID string
	SenderID    string
	SenderName  string
	ReceiverID  string
	GroupID     string
	ContentType int32
	ChatType    int32
	Content     []byte
	SendTime    int64
	CreateTime  int64
	Status      int32
	Platform    int32
	UniqueID    string
	Source      int32
	Attributes  map[string]string
}

type ReadSnapshot struct {
	ConversationID string
	ReadSeq        int64
	MaxSeq         int64
	UnreadCount    int64
}

type SeqRange struct {
	ConversationID string
	BeginSeq       int64
	EndSeq         int64
}

type PulledConversationMessages struct {
	ConversationID string
	EndSeq         int64
	Completed      bool
	Messages       []Message
}

type BootstrapData struct {
	Friends       []Friend
	Groups        []Group
	Conversations []Conversation
	MaxSeqs       map[string]int64
	ReadSnapshots map[string]ReadSnapshot
}
