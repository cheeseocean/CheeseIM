package auth

import (
	"context"

	"github.com/cheeseim/cheeseim-go-sdk/transport/tcpim"
	"github.com/cheeseim/cheeseim-go-sdk/types"
)

type TicketIssuer interface {
	IssueWsTicket(ctx context.Context, accessToken, deviceID, platform string) (types.WsTicket, error)
}

type AccessTokenIssuer interface {
	Login(ctx context.Context, userID, password string, platformID int, deviceID, clientVersion string) (string, error)
}

type AuthConnector interface {
	Connect(ctx context.Context, address, ticket string) (string, error)
}

type AuthSession struct {
	AccessToken string
	Ticket      types.WsTicket
	UserID      string
}

type RealtimeEventSource interface {
	Events() <-chan tcpim.Event
}

type AuthService struct {
	tokens  AccessTokenIssuer
	tickets TicketIssuer
	tcp     AuthConnector
}

func NewAuthService(tokens AccessTokenIssuer, tickets TicketIssuer, tcp AuthConnector) *AuthService {
	return &AuthService{tokens: tokens, tickets: tickets, tcp: tcp}
}

func (s *AuthService) Login(ctx context.Context, userID, password, deviceID, platform, tcpAddr string) (AuthSession, error) {
	if s.tokens == nil {
		return AuthSession{}, context.Canceled
	}
	accessToken, err := s.tokens.Login(ctx, userID, password, platformID(platform), deviceID, "CheeseBox/dev")
	if err != nil {
		return AuthSession{}, err
	}
	return s.LoginWithToken(ctx, accessToken, deviceID, platform, tcpAddr)
}

func (s *AuthService) LoginWithToken(ctx context.Context, apiToken, deviceID, platform, tcpAddr string) (AuthSession, error) {
	ticket, err := s.tickets.IssueWsTicket(ctx, apiToken, deviceID, platform)
	if err != nil {
		return AuthSession{}, err
	}
	userID, err := s.tcp.Connect(ctx, tcpAddr, ticket.Ticket)
	if err != nil {
		return AuthSession{}, err
	}
	return AuthSession{AccessToken: apiToken, Ticket: ticket, UserID: userID}, nil
}

func (s *AuthService) Reconnect(ctx context.Context, apiToken, deviceID, platform, tcpAddr string) (AuthSession, error) {
	return s.LoginWithToken(ctx, apiToken, deviceID, platform, tcpAddr)
}

func (s *AuthService) Events() <-chan tcpim.Event {
	source, ok := s.tcp.(RealtimeEventSource)
	if !ok {
		return nil
	}
	return source.Events()
}

func platformID(platform string) int {
	switch platform {
	case "ios":
		return 1
	case "android":
		return 2
	case "windows":
		return 3
	case "osx", "mac", "macos":
		return 4
	case "web":
		return 5
	case "miniweb", "mini_web":
		return 6
	case "linux":
		return 7
	case "cli":
		return 8
	default:
		return 0
	}
}
