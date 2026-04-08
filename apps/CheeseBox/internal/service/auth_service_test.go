package service

import (
	"context"
	"errors"
	"testing"

	"github.com/cheeseim/cheesebox/internal/domain"
)

func TestAuthServiceLogin(t *testing.T) {
	issuer := &fakeTicketIssuer{ticket: domain.WsTicket{Ticket: "ticket-1"}}
	connector := &fakeAuthConnector{}

	service := NewAuthService(issuer, connector)
	ticket, err := service.Login(context.Background(), "token-1", "device-1", "desktop", "127.0.0.1:9000")
	if err != nil {
		t.Fatalf("Login() error = %v", err)
	}
	if ticket.Ticket != "ticket-1" {
		t.Fatalf("ticket = %#v", ticket)
	}
	if connector.ticket != "ticket-1" || connector.address != "127.0.0.1:9000" {
		t.Fatalf("connector = %#v", connector)
	}
}

func TestAuthServiceLoginReturnsTicketError(t *testing.T) {
	service := NewAuthService(&fakeTicketIssuer{err: errors.New("boom")}, &fakeAuthConnector{})
	if _, err := service.Login(context.Background(), "token-1", "device-1", "desktop", "127.0.0.1:9000"); err == nil {
		t.Fatal("Login() error = nil, want non-nil")
	}
}

type fakeTicketIssuer struct {
	ticket domain.WsTicket
	err    error
}

func (f *fakeTicketIssuer) IssueWsTicket(context.Context, string, string, string) (domain.WsTicket, error) {
	return f.ticket, f.err
}

type fakeAuthConnector struct {
	address string
	ticket  string
	err     error
}

func (f *fakeAuthConnector) Connect(_ context.Context, address, ticket string) error {
	f.address = address
	f.ticket = ticket
	return f.err
}
