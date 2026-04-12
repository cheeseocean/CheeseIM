package ui

import (
	"context"
	"errors"
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestRootModelLoginSuccessTransitionsToApp(t *testing.T) {
	client := &fakeIMClient{
		currentUserID: "user-1",
		loginData: sdktypes.BootstrapData{
			Friends:       []sdktypes.Friend{{UserID: "user-1", DisplayName: "Alice"}},
			Groups:        []sdktypes.Group{{GroupID: "group-1", GroupName: "Crew"}},
			Conversations: []sdktypes.Conversation{{ConversationID: "s:user-1:user-2", Title: "Alice", LastMessageTime: 1}},
		},
		events: make(chan sdktypes.Event, 1),
	}
	model := NewRootModel(config.RuntimeConfig{
		APIBaseURL: "http://127.0.0.1:8080",
		TCPAddr:    "127.0.0.1:9000",
		DeviceID:   "device-1",
		Platform:   "desktop",
	}, client)
	model.login.inputs[0].SetValue("user-1")
	model.login.inputs[1].SetValue("secret")

	updated, cmd := model.Update(LoginSubmittedMsg{})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if model.appStore.ConnectionStatus != domain.ConnectionStatusConnected {
		t.Fatalf("status = %q, want connected", model.appStore.ConnectionStatus)
	}
	if len(model.appStore.Friends) != 1 || len(model.appStore.Groups) != 1 || len(model.appStore.ConversationOrder) != 1 {
		t.Fatalf("store = %#v", model.appStore)
	}
	if model.appStore.CurrentUserID != "user-1" {
		t.Fatalf("current user = %q", model.appStore.CurrentUserID)
	}
}

func TestRootModelLoginErrorShowsToast(t *testing.T) {
	client := &fakeIMClient{loginErr: context.DeadlineExceeded}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.login.inputs[0].SetValue("user-1")

	updated, cmd := model.Update(LoginSubmittedMsg{})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if !strings.Contains(model.View(), "context deadline exceeded") {
		t.Fatalf("view = %q", model.View())
	}
}

func TestRootModelReconnectCommand(t *testing.T) {
	client := &fakeIMClient{
		currentUserID: "user-1",
		events:        make(chan sdktypes.Event, 1),
	}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(ReconnectMsg{})
	model = updated.(RootModel)
	if cmd == nil {
		t.Fatal("Reconnect command is nil")
	}
}

func TestRootModelOpenConversationLoadsHistory(t *testing.T) {
	client := &fakeIMClient{
		currentUserID: "user-1",
		history: []sdktypes.Message{{
			Sequence:    11,
			ServerMsgID: "server-1",
			SenderID:    "user-2",
			Content:     []byte("hello"),
		}},
		events: make(chan sdktypes.Event, 1),
	}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(OpenConversationMsg{ConversationID: "s:user-1:user-2"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if model.appStore.ActiveConversation != "s:user-1:user-2" {
		t.Fatalf("active conversation = %q", model.appStore.ActiveConversation)
	}
	if len(model.appStore.MessagesByConv["s:user-1:user-2"]) != 1 {
		t.Fatalf("messages = %#v", model.appStore.MessagesByConv)
	}
	if client.markReadConversation != "s:user-1:user-2" || client.markReadSeq != 11 {
		t.Fatalf("mark read = %q %d", client.markReadConversation, client.markReadSeq)
	}
}

func TestRootModelOpenConversationErrorDoesNotReturnToLogin(t *testing.T) {
	client := &fakeIMClient{
		currentUserID: "user-1",
		openErr:       context.DeadlineExceeded,
		events:        make(chan sdktypes.Event, 1),
	}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(OpenConversationMsg{ConversationID: "s:user-1:user-2"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if model.appStore.ConnectionStatus != domain.ConnectionStatusConnected {
		t.Fatalf("status = %q, want connected", model.appStore.ConnectionStatus)
	}
	if strings.Contains(model.View(), "Login") {
		t.Fatalf("view = %q", model.View())
	}
	if !strings.Contains(model.View(), "context deadline exceeded") {
		t.Fatalf("view = %q", model.View())
	}
}

func TestNewRequestIDStaysWithinTcpLimit(t *testing.T) {
	requestID := newRequestID()
	if len(requestID) > 16 {
		t.Fatalf("len(requestID) = %d, want <= 16", len(requestID))
	}
}

func TestRootModelSubmitInputSendsMessage(t *testing.T) {
	client := &fakeIMClient{
		currentUserID: "user-1",
		sendResult: sdktypes.Message{
			ClientMsgID: "client-1",
			SenderID:    "user-1",
			Content:     []byte("hello"),
		},
	}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetActiveConversation("s:user-1:user-2")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(SubmitInputMsg{Text: "hello"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if len(model.appStore.MessagesByConv["s:user-1:user-2"]) != 1 {
		t.Fatalf("messages = %#v", model.appStore.MessagesByConv)
	}
	if client.sentConversationID != "s:user-1:user-2" || string(client.sentText) != "hello" {
		t.Fatalf("send = %#v", client)
	}
}

func TestRootModelSubmitInputAddFriend(t *testing.T) {
	client := &fakeIMClient{}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(SubmitInputMsg{Text: "/addfriend user-2 hi there"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if client.addFriendUserID != "user-2" || client.addFriendMessage != "hi there" {
		t.Fatalf("client = %#v", client)
	}
	if model.appStore.Toast.Kind != domain.ToastKindSuccess {
		t.Fatalf("toast = %#v", model.appStore.Toast)
	}
}

func TestRootModelRealtimeMessageAppendsToConversation(t *testing.T) {
	client := &fakeIMClient{}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, _ := model.Update(realtimeEventMsg{event: sdktypes.Event{
		Kind:           sdktypes.EventKindRealtime,
		ConversationID: "s:user-1:user-2",
		Message: &sdktypes.Message{
			ServerMsgID: "server-1",
			SenderID:    "user-2",
			Content:     []byte("hello"),
		},
	}})
	model = updated.(RootModel)

	items := model.appStore.MessagesByConv["s:user-1:user-2"]
	if len(items) != 1 || items[0].Content != "hello" {
		t.Fatalf("messages = %#v", model.appStore.MessagesByConv)
	}
	if model.appStore.Conversations["s:user-1:user-2"].UnreadCount != 1 {
		t.Fatalf("summary = %#v", model.appStore.Conversations["s:user-1:user-2"])
	}
}

func TestRootModelLoginSuccessStartsRealtimeListener(t *testing.T) {
	events := make(chan sdktypes.Event, 1)
	client := &fakeIMClient{
		currentUserID: "user-1",
		events:        events,
	}
	model := NewRootModel(config.RuntimeConfig{}, client)

	updated, cmd := model.Update(loginSuccessMsg{
		data: sdktypes.BootstrapData{},
	})
	model = updated.(RootModel)
	if cmd == nil {
		t.Fatal("login success cmd is nil")
	}

	events <- sdktypes.Event{Kind: sdktypes.EventKindDisconnected}
	msg := cmd()
	realtime, ok := msg.(realtimeEventMsg)
	if !ok || realtime.event.Kind != sdktypes.EventKindDisconnected {
		t.Fatalf("msg = %#v, want realtime disconnect", msg)
	}
}

type fakeIMClient struct {
	currentUserID       string
	loginData           sdktypes.BootstrapData
	loginErr            error
	reconnectData       sdktypes.BootstrapData
	reconnectErr        error
	history             []sdktypes.Message
	openErr             error
	sendResult          sdktypes.Message
	sendErr             error
	sentConversationID  string
	sentText            string
	addFriendUserID     string
	addFriendMessage    string
	addFriendErr        error
	markReadConversation string
	markReadSeq         int64
	markReadErr         error
	events              chan sdktypes.Event
}

func (f *fakeIMClient) Login(context.Context, string, string) (sdktypes.BootstrapData, error) {
	return f.loginData, f.loginErr
}

func (f *fakeIMClient) Reconnect(context.Context) (sdktypes.BootstrapData, error) {
	return f.reconnectData, f.reconnectErr
}

func (f *fakeIMClient) OpenConversation(context.Context, string, int) ([]sdktypes.Message, error) {
	if f.openErr != nil {
		return nil, f.openErr
	}
	return f.history, nil
}

func (f *fakeIMClient) SendText(_ string, conversationID, text string) (sdktypes.Message, error) {
	f.sentConversationID = conversationID
	f.sentText = text
	return f.sendResult, f.sendErr
}

func (f *fakeIMClient) AddFriend(_ context.Context, friendUserID, message string) error {
	f.addFriendUserID = friendUserID
	f.addFriendMessage = message
	return f.addFriendErr
}

func (f *fakeIMClient) MarkRead(_ context.Context, conversationID string, readSeq int64) (sdktypes.ReadSnapshot, error) {
	f.markReadConversation = conversationID
	f.markReadSeq = readSeq
	return sdktypes.ReadSnapshot{ConversationID: conversationID, ReadSeq: readSeq}, f.markReadErr
}

func (f *fakeIMClient) Events() <-chan sdktypes.Event {
	return f.events
}

func (f *fakeIMClient) CurrentUserID() string {
	return f.currentUserID
}

var _ IMClient = (*fakeIMClient)(nil)

func TestRootModelHandlesRealtimeError(t *testing.T) {
	client := &fakeIMClient{}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, _ := model.Update(realtimeEventMsg{event: sdktypes.Event{
		Kind: sdktypes.EventKindError,
		Err:  errors.New("boom"),
	}})
	model = updated.(RootModel)

	if model.appStore.Toast.Message != "boom" {
		t.Fatalf("toast = %#v", model.appStore.Toast)
	}
}

func TestRootModelDisconnectDoesNotReturnToLogin(t *testing.T) {
	client := &fakeIMClient{currentUserID: "user-1"}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, _ := model.Update(realtimeEventMsg{event: sdktypes.Event{
		Kind: sdktypes.EventKindDisconnected,
	}})
	model = updated.(RootModel)

	if model.appStore.ConnectionStatus != domain.ConnectionStatusDisconnected {
		t.Fatalf("status = %q, want disconnected", model.appStore.ConnectionStatus)
	}
	if strings.Contains(model.View(), "User ID") {
		t.Fatalf("view = %q, want app without login panel", model.View())
	}
	if !strings.Contains(model.View(), "状态: disconnected") {
		t.Fatalf("view = %q, want disconnected status", model.View())
	}
}

func TestRootModelLoginInputReceivesTypedCharacters(t *testing.T) {
	// t/l 等字母在登录态应直接录入输入框，而非触发全局快捷键
	model := NewRootModel(config.RuntimeConfig{}, &fakeIMClient{})
	for _, ch := range []rune{'l', 't'} {
		updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune{ch}})
		model = updated.(RootModel)
	}
	if model.login.inputs[0].Value() != "lt" {
		t.Fatalf("input = %q, want \"lt\"", model.login.inputs[0].Value())
	}
}
