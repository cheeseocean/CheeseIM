package ui

import (
	"context"
	"strings"
	"testing"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	pb "github.com/cheeseim/cheesebox/internal/proto"
	"github.com/cheeseim/cheesebox/internal/service"
	"github.com/cheeseim/cheesebox/internal/transport/tcpim"
)

func TestRootModelLoginSuccessTransitionsToApp(t *testing.T) {
	auth := service.NewAuthService(&rootAccessTokenIssuer{token: "token-1"}, &rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{userID: "user-1"})
	roster := service.NewRosterService(&rootRosterClient{
		friends:       []domain.FriendSummary{{UserID: "user-1", DisplayName: "Alice"}},
		groups:        []domain.GroupSummary{{GroupID: "group-1", GroupName: "Crew"}},
		conversations: []domain.ConversationSummary{{ConversationID: "c1:user-1:user-2", Title: "Alice", LastMessageTime: 1}},
	})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	contacts := service.NewContactService(&rootFriendRequester{})
	model := NewRootModel(config.RuntimeConfig{
		APIBaseURL: "http://127.0.0.1:8080",
		TCPAddr:    "127.0.0.1:9000",
		DeviceID:   "device-1",
		Platform:   "desktop",
	}, auth, roster, chat, contacts)
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
	if model.token != "token-1" {
		t.Fatalf("token = %q, want token-1", model.token)
	}
}

func TestRootModelLoginErrorShowsToast(t *testing.T) {
	auth := service.NewAuthService(&rootAccessTokenIssuer{}, &rootTicketIssuer{}, &rootConnector{err: context.DeadlineExceeded})
	roster := service.NewRosterService(&rootRosterClient{})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{}, auth, roster, chat, service.NewContactService(&rootFriendRequester{}))
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
	auth := service.NewAuthService(&rootAccessTokenIssuer{token: "token-1"}, &rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{userID: "user-1"})
	roster := service.NewRosterService(&rootRosterClient{})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	contacts := service.NewContactService(&rootFriendRequester{})
	model := NewRootModel(config.RuntimeConfig{
		TCPAddr:  "127.0.0.1:9000",
		DeviceID: "device-1",
		Platform: "desktop",
	}, auth, roster, chat, contacts)
	model.token = "token-1"

	updated, cmd := model.Update(ReconnectMsg{})
	model = updated.(RootModel)
	if cmd == nil {
		t.Fatal("Reconnect command is nil")
	}
}

func TestRootModelOpenConversationLoadsHistory(t *testing.T) {
	auth := service.NewAuthService(&rootAccessTokenIssuer{token: "token-1"}, &rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{userID: "user-1"})
	historyClient := &rootRosterClient{
		history: []domain.HistoryMessage{{ServerMsgID: "server-1", SenderID: "user-2", Content: "hello"}},
	}
	roster := service.NewRosterService(historyClient)
	chat := service.NewChatService(&rootChatSender{}, historyClient)
	model := NewRootModel(config.RuntimeConfig{}, auth, roster, chat, service.NewContactService(&rootFriendRequester{}))
	model.token = "token-1"
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(OpenConversationMsg{ConversationID: "c1:user-1:user-2"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if model.appStore.ActiveConversation != "c1:user-1:user-2" {
		t.Fatalf("active conversation = %q", model.appStore.ActiveConversation)
	}
	if len(model.appStore.MessagesByConv["c1:user-1:user-2"]) != 1 {
		t.Fatalf("messages = %#v", model.appStore.MessagesByConv)
	}
}

func TestRootModelOpenConversationErrorDoesNotReturnToLogin(t *testing.T) {
	auth := service.NewAuthService(&rootAccessTokenIssuer{token: "token-1"}, &rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{userID: "user-1"})
	roster := service.NewRosterService(&rootRosterClient{})
	chat := service.NewChatService(&rootChatSender{}, &errorHistoryClient{err: context.DeadlineExceeded})
	model := NewRootModel(config.RuntimeConfig{}, auth, roster, chat, service.NewContactService(&rootFriendRequester{}))
	model.token = "token-1"
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
	chatSender := &rootChatSender{}
	chat := service.NewChatService(chatSender, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{}, service.NewAuthService(&rootAccessTokenIssuer{}, &rootTicketIssuer{}, &rootConnector{}), service.NewRosterService(&rootRosterClient{}), chat, service.NewContactService(&rootFriendRequester{}))
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetActiveConversation("s:user-1:user-2")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
	model.token = "token-1"

	updated, cmd := model.Update(SubmitInputMsg{Text: "hello"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if len(model.appStore.MessagesByConv["s:user-1:user-2"]) != 1 {
		t.Fatalf("messages = %#v", model.appStore.MessagesByConv)
	}
	if chatSender.message == nil || chatSender.message.GetReceiverId() != "user-2" || string(chatSender.message.GetContent()) != "hello" {
		t.Fatalf("outbound message = %#v", chatSender.message)
	}
}

func TestRootModelSubmitInputAddFriend(t *testing.T) {
	requester := &rootFriendRequester{}
	model := NewRootModel(config.RuntimeConfig{}, service.NewAuthService(&rootAccessTokenIssuer{}, &rootTicketIssuer{}, &rootConnector{}), service.NewRosterService(&rootRosterClient{}), service.NewChatService(&rootChatSender{}, &rootRosterClient{}), service.NewContactService(requester))
	model.token = "token-1"
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, cmd := model.Update(SubmitInputMsg{Text: "/addfriend user-2 hi there"})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if requester.friendUserID != "user-2" || requester.message != "hi there" || requester.accessToken != "token-1" {
		t.Fatalf("requester = %#v", requester)
	}
	if model.appStore.Toast.Kind != domain.ToastKindSuccess {
		t.Fatalf("toast = %#v", model.appStore.Toast)
	}
}

func TestRootModelRealtimeMessageAppendsToConversation(t *testing.T) {
	connector := &rootConnector{events: make(chan tcpim.Event, 1)}
	auth := service.NewAuthService(&rootAccessTokenIssuer{}, &rootTicketIssuer{}, connector)
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{}, auth, service.NewRosterService(&rootRosterClient{}), chat, service.NewContactService(&rootFriendRequester{}))
	model.token = "token-1"
	model.appStore.SetCurrentUserID("user-1")
	model.appStore.SetConnectionStatus(domain.ConnectionStatusConnected)

	updated, _ := model.Update(realtimeEventMsg{event: tcpim.Event{
		Kind: tcpim.EventMessage,
		Message: &pb.ProtoMessage{
			ServerMsgId: "server-1",
			SenderId:    "user-2",
			ReceiverId:  "user-1",
			SessionType: 1,
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
	events := make(chan tcpim.Event, 1)
	auth := service.NewAuthService(&rootAccessTokenIssuer{}, &rootTicketIssuer{}, &rootConnector{events: events})
	model := NewRootModel(config.RuntimeConfig{}, auth, service.NewRosterService(&rootRosterClient{}), service.NewChatService(&rootChatSender{}, &rootRosterClient{}), service.NewContactService(&rootFriendRequester{}))

	updated, cmd := model.Update(loginSuccessMsg{
		userID: "user-1",
		token:  "token-1",
		data:   service.InitialData{},
	})
	model = updated.(RootModel)
	if cmd == nil {
		t.Fatal("login success cmd is nil")
	}

	events <- tcpim.Event{Kind: tcpim.EventDisconnect}
	msg := cmd()
	realtime, ok := msg.(realtimeEventMsg)
	if !ok || realtime.event.Kind != tcpim.EventDisconnect {
		t.Fatalf("msg = %#v, want realtime disconnect", msg)
	}
}

type rootTicketIssuer struct {
	ticket domain.WsTicket
	err    error
}

func (r *rootTicketIssuer) IssueWsTicket(context.Context, string, string, string) (domain.WsTicket, error) {
	return r.ticket, r.err
}

type rootAccessTokenIssuer struct {
	token string
	err   error
}

func (r *rootAccessTokenIssuer) Login(context.Context, string, string, int, string, string) (string, error) {
	return r.token, r.err
}

type rootConnector struct {
	userID string
	err    error
	events chan tcpim.Event
}

func (r *rootConnector) Connect(context.Context, string, string) (string, error) {
	return r.userID, r.err
}

func (r *rootConnector) Events() <-chan tcpim.Event {
	return r.events
}

type rootRosterClient struct {
	friends       []domain.FriendSummary
	groups        []domain.GroupSummary
	conversations []domain.ConversationSummary
	history       []domain.HistoryMessage
}

func (r *rootRosterClient) ListFriends(context.Context, string) ([]domain.FriendSummary, error) {
	return r.friends, nil
}

func (r *rootRosterClient) ListGroups(context.Context, string) ([]domain.GroupSummary, error) {
	return r.groups, nil
}

func (r *rootRosterClient) ListConversations(context.Context, string) ([]domain.ConversationSummary, error) {
	return r.conversations, nil
}

func (r *rootRosterClient) LoadHistoryPage(context.Context, string, string, int) ([]domain.HistoryMessage, error) {
	return r.history, nil
}

type rootChatSender struct {
	requestID string
	message   *pb.ProtoMessage
	err       error
}

func (r *rootChatSender) SendChatMessage(requestID string, message *pb.ProtoMessage) error {
	r.requestID = requestID
	r.message = message
	return r.err
}

type rootFriendRequester struct {
	accessToken  string
	friendUserID string
	message      string
	err          error
}

func (r *rootFriendRequester) AddFriend(_ context.Context, accessToken, friendUserID, message string) error {
	r.accessToken = accessToken
	r.friendUserID = friendUserID
	r.message = message
	return r.err
}

type errorHistoryClient struct {
	err error
}

func (e *errorHistoryClient) LoadHistoryPage(context.Context, string, string, int) ([]domain.HistoryMessage, error) {
	return nil, e.err
}
