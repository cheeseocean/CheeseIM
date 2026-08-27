package domain

import "time"

type ConversationKind int

const (
	ConversationKindDirect ConversationKind = iota
	ConversationKindGroup
)

type ConversationRef struct {
	Kind ConversationKind
	ID   string
}

func NewConversationRefDirect(userID string) ConversationRef {
	return ConversationRef{
		Kind: ConversationKindDirect,
		ID:   userID,
	}
}

func NewConversationRefGroup(groupID string) ConversationRef {
	return ConversationRef{
		Kind: ConversationKindGroup,
		ID:   groupID,
	}
}

type MessageItem struct {
	ID              string
	ConversationID  string
	Sequence        int64
	ClientMsgID     string
	ServerMsgID     string
	SenderID        string
	SenderLabel     string
	Content         string
	Self            bool
	SendTime        int64
	CreateTime      int64
	DeliveryState   string
	Revoked         bool
	RevokedBy       string
	RevokedAt       int64
	MutationVersion int64
}

type RevokeInfo struct {
	ServerMsgID     string
	OperatorUserID  string
	OperatorName    string
	RevokedAt       int64
	MutationVersion int64
}

type TypingIndicator struct {
	SenderID    string
	SenderLabel string
	ExpiresAt   int64
}

func NewMessageItem(senderID, content string) MessageItem {
	return MessageItem{
		SenderID: senderID,
		Content:  content,
	}
}

func (m MessageItem) WithSelf(userID string) MessageItem {
	m.Self = m.SenderID == userID
	return m
}

type NavKey string

const (
	NavKeyChats    NavKey = "chats"
	NavKeyFriends  NavKey = "friends"
	NavKeyGroups   NavKey = "groups"
	NavKeySettings NavKey = "settings"
)

type ConnectionStatus string

const (
	ConnectionStatusLoggedOut    ConnectionStatus = "logged_out"
	ConnectionStatusDisconnected ConnectionStatus = "disconnected"
	ConnectionStatusConnecting   ConnectionStatus = "connecting"
	ConnectionStatusSyncing      ConnectionStatus = "syncing"
	ConnectionStatusConnected    ConnectionStatus = "connected"
	ConnectionStatusError        ConnectionStatus = "error"
)

type ToastKind string

const (
	ToastKindInfo    ToastKind = "info"
	ToastKindSuccess ToastKind = "success"
	ToastKindWarning ToastKind = "warning"
	ToastKindError   ToastKind = "error"
)

type Toast struct {
	Kind    ToastKind
	Message string
	At      time.Time
}

type FriendSummary struct {
	UserID      string
	DisplayName string
	AvatarSeed  string
}

type FriendRequestSummary struct {
	UserID         string
	RequestMessage string
	Status         int
	CreateTime     int64
}

type GroupSummary struct {
	GroupID   string
	GroupName string
	FaceURL   string
}

type ConversationSummary struct {
	ConversationID     string
	Title              string
	Subtitle           string
	Kind               ConversationKind
	LastMessagePreview string
	LastMessageTime    int64
	UnreadCount        int
}

type HistoryMessage struct {
	Sequence    int64
	ServerMsgID string
	SenderID    string
	SenderName  string
	Content     string
	SendTime    int64
}

type WsTicket struct {
	Ticket   string
	ExpireAt int64
	WSURL    string
}
