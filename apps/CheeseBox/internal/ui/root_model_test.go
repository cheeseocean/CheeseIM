package ui

import (
	"context"
	"errors"
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
	"github.com/cheeseim/cheesebox/internal/sync"
	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestRootModelLoginSuccessTransitionsToApp(t *testing.T) {
	withPersistedStoreFactory(t, func(string) store.Persister {
		return &fakePersister{}
	})
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

func TestRootModelLoginSuccessLoadsUserScopedConversationCursor(t *testing.T) {
	cursor := sdktypes.ConversationSyncCursor{VersionID: "v-user-1", Version: 7, IDHash: 99}
	withPersistedStoreFactory(t, func(userID string) store.Persister {
		if userID != "user-1" {
			t.Fatalf("userID = %q, want user-1", userID)
		}
		return &fakePersister{cursor: cursor}
	})
	client := &fakeIMClient{
		currentUserID: "user-1",
		events:        make(chan sdktypes.Event, 1),
	}
	model := NewRootModel(config.RuntimeConfig{}, client)

	updated, _ := model.Update(loginSuccessMsg{data: sdktypes.BootstrapData{}})
	model = updated.(RootModel)

	if client.conversationCursor != cursor {
		t.Fatalf("client cursor = %#v, want %#v", client.conversationCursor, cursor)
	}
	if model.appStore.ConversationCursor != cursor {
		t.Fatalf("store cursor = %#v, want %#v", model.appStore.ConversationCursor, cursor)
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
	item := model.appStore.MessagesByConv["s:user-1:user-2"][0]
	if item.Sequence != 11 || item.ServerMsgID != "server-1" {
		t.Fatalf("message metadata = %#v", item)
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

func TestRootModelSubmitInputOpensCanonicalDirectChat(t *testing.T) {
	model := NewRootModel(config.RuntimeConfig{}, &fakeIMClient{})
	model.appStore.SetCurrentUserID("user-2")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	_, cmd := model.Update(SubmitInputMsg{Text: "/chat user-1"})
	if cmd == nil {
		t.Fatal("cmd = nil")
	}
	msg, ok := cmd().(OpenConversationMsg)
	if !ok || msg.ConversationID != "s:user-1:user-2" {
		t.Fatalf("message = %#v", msg)
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

func TestRootModelRealtimeAppliesSyncerMergedMessages(t *testing.T) {
	client := &fakeIMClient{
		pulled: []sdktypes.PulledConversationMessages{
			{
				ConversationID: "s:user-1:user-2",
				Messages: []sdktypes.Message{
					{Sequence: 2, SenderID: "user-2", ReceiverID: "user-1", ChatType: 1, Content: []byte("two")},
				},
			},
		},
		serverMaxSeqs: map[string]int64{"s:user-1:user-2": 3},
	}
	model := NewRootModel(config.RuntimeConfig{}, client)
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
	memory := sync.NewMemoryStore()
	memory.SetMessages("s:user-1:user-2", []sdktypes.Message{
		{Sequence: 1, SenderID: "user-1", ReceiverID: "user-2", ChatType: 1, Content: []byte("one")},
	})
	model.syncer = sync.NewSyncer(memory, sync.NewSDKPuller(client), client.GetServerMaxSeq, client.UpdateSyncedMaxSeq)

	updated, _ := model.Update(realtimeEventMsg{event: sdktypes.Event{
		Kind:           sdktypes.EventKindRealtime,
		ConversationID: "s:user-1:user-2",
		Message: &sdktypes.Message{
			Sequence:    3,
			ServerMsgID: "server-3",
			SenderID:    "user-2",
			ReceiverID:  "user-1",
			ChatType:    1,
			Content:     []byte("three"),
		},
	}})
	model = updated.(RootModel)

	items := model.appStore.MessagesByConv["s:user-1:user-2"]
	if len(items) != 3 {
		t.Fatalf("messages = %#v", items)
	}
	for i, want := range []int64{1, 2, 3} {
		if items[i].Sequence != want {
			t.Fatalf("messages[%d].Sequence = %d, want %d", i, items[i].Sequence, want)
		}
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
	if batch, ok := msg.(tea.BatchMsg); ok && len(batch) == 2 {
		msg = batch[1]()
	}
	realtime, ok := msg.(realtimeEventMsg)
	if !ok || realtime.event.Kind != sdktypes.EventKindDisconnected {
		t.Fatalf("msg = %#v, want realtime disconnect", msg)
	}
}

type fakeIMClient struct {
	currentUserID          string
	loginData              sdktypes.BootstrapData
	loginErr               error
	reconnectData          sdktypes.BootstrapData
	reconnectErr           error
	history                []sdktypes.Message
	openErr                error
	sendResult             sdktypes.Message
	sendErr                error
	sentConversationID     string
	sentText               string
	addFriendUserID        string
	addFriendMessage       string
	addFriendErr           error
	markReadConversation   string
	markReadSeq            int64
	markReadErr            error
	events                 chan sdktypes.Event
	pulled                 []sdktypes.PulledConversationMessages
	serverMaxSeqs          map[string]int64
	syncedMaxSeqs          map[string]int64
	conversationCursor     sdktypes.ConversationSyncCursor
	conversationSyncResult sdktypes.ConversationSyncResult
	conversationSyncErr    error
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

func (f *fakeIMClient) PullMessages(context.Context, []sdktypes.SeqRange, int64) ([]sdktypes.PulledConversationMessages, error) {
	return f.pulled, nil
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

func (f *fakeIMClient) MarkRead(_ context.Context, conversationID string, readSeq int64) error {
	f.markReadConversation = conversationID
	f.markReadSeq = readSeq
	return f.markReadErr
}

func (f *fakeIMClient) AckDelivered(string, int64) error {
	return nil
}

func (f *fakeIMClient) Events() <-chan sdktypes.Event {
	return f.events
}

func (f *fakeIMClient) CurrentUserID() string {
	return f.currentUserID
}

func (f *fakeIMClient) GetSyncedMaxSeq(conversationID string) int64 {
	if f.syncedMaxSeqs == nil {
		return 0
	}
	return f.syncedMaxSeqs[conversationID]
}

func (f *fakeIMClient) GetServerMaxSeq(conversationID string) int64 {
	if f.serverMaxSeqs == nil {
		return 0
	}
	return f.serverMaxSeqs[conversationID]
}

func (f *fakeIMClient) UpdateSyncedMaxSeq(conversationID string, seq int64) {
	if f.syncedMaxSeqs == nil {
		f.syncedMaxSeqs = make(map[string]int64)
	}
	if seq > f.syncedMaxSeqs[conversationID] {
		f.syncedMaxSeqs[conversationID] = seq
	}
}

func (f *fakeIMClient) GetConversationCursor() sdktypes.ConversationSyncCursor {
	return f.conversationCursor
}

func (f *fakeIMClient) UpdateConversationCursor(cursor sdktypes.ConversationSyncCursor) {
	f.conversationCursor = cursor
}

func (f *fakeIMClient) SyncConversations(context.Context) (sdktypes.ConversationSyncResult, error) {
	if f.conversationSyncErr != nil {
		return sdktypes.ConversationSyncResult{}, f.conversationSyncErr
	}
	return f.conversationSyncResult, nil
}

var _ IMClient = (*fakeIMClient)(nil)

func withPersistedStoreFactory(t *testing.T, factory func(userID string) store.Persister) {
	t.Helper()
	previous := newUserPersister
	newUserPersister = func(userID string) (store.Persister, error) {
		return factory(userID), nil
	}
	t.Cleanup(func() {
		newUserPersister = previous
	})
}

type fakePersister struct {
	messages      map[string][]store.MessageRecord
	conversations map[string]store.ConversationRecord
	cursor        sdktypes.ConversationSyncCursor
}

func (f *fakePersister) GetMessages(conversationID string) []store.MessageRecord {
	return append([]store.MessageRecord(nil), f.messages[conversationID]...)
}

func (f *fakePersister) AppendMessage(conversationID string, msg store.MessageRecord) {
	if f.messages == nil {
		f.messages = make(map[string][]store.MessageRecord)
	}
	f.messages[conversationID] = append(f.messages[conversationID], msg)
}

func (f *fakePersister) SetMessages(conversationID string, msgs []store.MessageRecord) {
	if f.messages == nil {
		f.messages = make(map[string][]store.MessageRecord)
	}
	f.messages[conversationID] = append([]store.MessageRecord(nil), msgs...)
}

func (f *fakePersister) GetConversations() map[string]store.ConversationRecord {
	result := make(map[string]store.ConversationRecord, len(f.conversations))
	for key, value := range f.conversations {
		result[key] = value
	}
	return result
}

func (f *fakePersister) UpsertConversation(conv store.ConversationRecord) {
	if f.conversations == nil {
		f.conversations = make(map[string]store.ConversationRecord)
	}
	f.conversations[conv.ConversationID] = conv
}

func (f *fakePersister) GetConversationCursor() sdktypes.ConversationSyncCursor {
	return f.cursor
}

func (f *fakePersister) SetConversationCursor(cursor sdktypes.ConversationSyncCursor) {
	f.cursor = cursor
}

func (f *fakePersister) ClearMessages(conversationID string) {
	delete(f.messages, conversationID)
}

func (f *fakePersister) Clear() {
	f.messages = make(map[string][]store.MessageRecord)
	f.conversations = make(map[string]store.ConversationRecord)
	f.cursor = sdktypes.ConversationSyncCursor{}
}

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
