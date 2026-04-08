package ui

import (
	"context"
	"strings"
	"testing"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	pb "github.com/cheeseim/cheesebox/internal/proto"
	"github.com/cheeseim/cheesebox/internal/service"
)

func TestRootModelLoginSuccessTransitionsToApp(t *testing.T) {
	auth := service.NewAuthService(&rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{})
	roster := service.NewRosterService(&rootRosterClient{
		friends:       []domain.FriendSummary{{UserID: "user-1", DisplayName: "Alice"}},
		groups:        []domain.GroupSummary{{GroupID: "group-1", GroupName: "Crew"}},
		conversations: []domain.ConversationSummary{{ConversationID: "c1:user-1:user-2", Title: "Alice", LastMessageTime: 1}},
	})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{
		APIBaseURL: "http://127.0.0.1:8080",
		TCPAddr:    "127.0.0.1:9000",
		DeviceID:   "device-1",
		Platform:   "desktop",
	}, auth, roster, chat)
	model.login.inputs[2].SetValue("token-1")

	updated, cmd := model.Update(LoginSubmittedMsg{})
	model = updated.(RootModel)
	msg := cmd()
	updated, _ = model.Update(msg)
	model = updated.(RootModel)

	if model.screen != screenApp {
		t.Fatalf("screen = %q, want app", model.screen)
	}
	if model.appStore.ConnectionStatus != domain.ConnectionStatusConnected {
		t.Fatalf("status = %q, want connected", model.appStore.ConnectionStatus)
	}
	if len(model.appStore.Friends) != 1 || len(model.appStore.Groups) != 1 || len(model.appStore.ConversationOrder) != 1 {
		t.Fatalf("store = %#v", model.appStore)
	}
}

func TestRootModelLoginErrorShowsToast(t *testing.T) {
	auth := service.NewAuthService(&rootTicketIssuer{}, &rootConnector{err: context.DeadlineExceeded})
	roster := service.NewRosterService(&rootRosterClient{})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{}, auth, roster, chat)
	model.login.inputs[2].SetValue("token-1")

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
	auth := service.NewAuthService(&rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{})
	roster := service.NewRosterService(&rootRosterClient{})
	chat := service.NewChatService(&rootChatSender{}, &rootRosterClient{})
	model := NewRootModel(config.RuntimeConfig{
		TCPAddr:  "127.0.0.1:9000",
		DeviceID: "device-1",
		Platform: "desktop",
	}, auth, roster, chat)
	model.login.inputs[2].SetValue("token-1")
	model.screen = screenApp

	updated, cmd := model.Update(ReconnectMsg{})
	model = updated.(RootModel)
	if cmd == nil {
		t.Fatal("Reconnect command is nil")
	}
}

func TestRootModelOpenConversationLoadsHistory(t *testing.T) {
	auth := service.NewAuthService(&rootTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}, &rootConnector{})
	historyClient := &rootRosterClient{
		history: []domain.HistoryMessage{{ServerMsgID: "server-1", SenderID: "user-2", Content: "hello"}},
	}
	roster := service.NewRosterService(historyClient)
	chat := service.NewChatService(&rootChatSender{}, historyClient)
	model := NewRootModel(config.RuntimeConfig{}, auth, roster, chat)
	model.screen = screenApp
	model.token = "token-1"

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

type rootTicketIssuer struct {
	ticket domain.WsTicket
	err    error
}

func (r *rootTicketIssuer) IssueWsTicket(context.Context, string, string, string) (domain.WsTicket, error) {
	return r.ticket, r.err
}

type rootConnector struct {
	err error
}

func (r *rootConnector) Connect(context.Context, string, string) error {
	return r.err
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

type rootChatSender struct{}

func (r *rootChatSender) SendChatMessage(string, *pb.ProtoMessage) error { return nil }
