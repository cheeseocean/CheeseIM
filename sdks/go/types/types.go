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

type FriendRequestStatus int

const (
	FriendRequestRejected FriendRequestStatus = -1
	FriendRequestPending  FriendRequestStatus = 0
	FriendRequestAccepted FriendRequestStatus = 1
)

type FriendRequest struct {
	FromUserID     string
	ToUserID       string
	RequestMessage string
	Status         FriendRequestStatus
	HandleMessage  string
	HandlerUserID  string
	HandleTime     int64
	Extra          string
	CreateTime     int64
	UpdatedAt      int64
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

// MessageDeliveryState describes client-observable delivery progress for an outgoing message.
type MessageDeliveryState string

const (
	MessageDeliverySending        MessageDeliveryState = "sending"
	MessageDeliveryBrokerAccepted MessageDeliveryState = "broker_accepted"
	MessageDeliveryDelivered      MessageDeliveryState = "delivered"
	MessageDeliveryRead           MessageDeliveryState = "read"
)

type SendAck struct {
	ClientMsgID   string
	ServerMsgID   string
	AcceptedAt    int64
	AcceptedState int32
}

type DeliveryUpdate struct {
	ConversationID string
	RecipientID    string
	DeviceID       string
	DeliveredSeq   int64
	UpdatedAt      int64
}

type ReadUpdate struct {
	ConversationID string
	ReaderID       string
	ReadSeq        int64
	UpdatedAt      int64
}

type RevokeUpdate struct {
	ConversationID   string
	ServerMsgID      string
	OperatorUserID   string
	OperatorName     string
	TargetSenderID   string
	TargetSenderName string
	RevokedAt        int64
	MutationVersion  int64
}

type TypingAction int32

const (
	TypingActionStart TypingAction = 1
	TypingActionStop  TypingAction = 2
)

type TypingUpdate struct {
	ConversationID string
	SenderID       string
	Action         TypingAction
	ExpiresAt      int64
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

type ConversationSyncCursor struct {
	VersionID string
	Version   int64
	IDHash    int64
}

type ConversationSyncResult struct {
	ConversationSyncCursor
	Full   bool
	Insert []Conversation
	Update []Conversation
	Delete []string
}

type BootstrapData struct {
	Friends            []Friend
	Groups             []Group
	Conversations      []Conversation
	MaxSeqs            map[string]int64
	ReadSnapshots      map[string]ReadSnapshot
	ConversationCursor ConversationSyncCursor
}
