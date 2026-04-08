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
	SenderID string
	Content  string
	Self     bool
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
	ConnectionStatusDisconnected ConnectionStatus = "disconnected"
	ConnectionStatusConnecting   ConnectionStatus = "connecting"
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
