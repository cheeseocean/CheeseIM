package auth

import (
	"context"
	"errors"
	"testing"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestAuthServiceLogin(t *testing.T) {
	tokenIssuer := &fakeAccessTokenIssuer{token: "token-1"}
	issuer := &fakeTicketIssuer{ticket: types.WsTicket{Ticket: "ticket-1"}}
	connector := &fakeAuthConnector{userID: "user-1"}

	service := NewAuthService(tokenIssuer, issuer, connector)
	session, err := service.Login(context.Background(), "user-1", "secret", "device-1", "desktop", "127.0.0.1:9000")
	if err != nil {
		t.Fatalf("Login() error = %v", err)
	}
	if session.AccessToken != "token-1" || session.Ticket.Ticket != "ticket-1" || session.UserID != "user-1" {
		t.Fatalf("session = %#v", session)
	}
	if tokenIssuer.userID != "user-1" || tokenIssuer.identityAssertion != "secret" {
		t.Fatalf("token issuer = %#v", tokenIssuer)
	}
	if connector.ticket != "ticket-1" || connector.address != "127.0.0.1:9000" {
		t.Fatalf("connector = %#v", connector)
	}
}

func TestAuthServiceLoginReturnsTicketError(t *testing.T) {
	service := NewAuthService(&fakeAccessTokenIssuer{token: "token-1"}, &fakeTicketIssuer{err: errors.New("boom")}, &fakeAuthConnector{})
	if _, err := service.Login(context.Background(), "user-1", "secret", "device-1", "desktop", "127.0.0.1:9000"); err == nil {
		t.Fatal("Login() error = nil, want non-nil")
	}
}

func TestAuthServiceLoginUsesCliPlatformID(t *testing.T) {
	tokenIssuer := &fakeAccessTokenIssuer{token: "token-1"}
	service := NewAuthService(tokenIssuer, &fakeTicketIssuer{ticket: types.WsTicket{Ticket: "ticket-1"}}, &fakeAuthConnector{userID: "user-1"})

	if _, err := service.Login(context.Background(), "user-1", "secret", "device-1", "cli", "127.0.0.1:9000"); err != nil {
		t.Fatalf("Login() error = %v", err)
	}

	if got, want := tokenIssuer.platformID, 8; got != want {
		t.Fatalf("platformID = %d, want %d", got, want)
	}
}

type fakeAccessTokenIssuer struct {
	userID            string
	identityAssertion string
	platformID        int
	token             string
	err               error
}

func (f *fakeAccessTokenIssuer) Login(_ context.Context, userID, identityAssertion string, platformID int, _ string, _ string) (string, error) {
	f.userID = userID
	f.identityAssertion = identityAssertion
	f.platformID = platformID
	return f.token, f.err
}

type fakeTicketIssuer struct {
	ticket types.WsTicket
	err    error
}

func (f *fakeTicketIssuer) IssueWsTicket(context.Context, string, string, string) (types.WsTicket, error) {
	return f.ticket, f.err
}

type fakeAuthConnector struct {
	address string
	ticket  string
	userID  string
	err     error
}

func (f *fakeAuthConnector) Connect(_ context.Context, address, ticket string) (string, error) {
	f.address = address
	f.ticket = ticket
	return f.userID, f.err
}
