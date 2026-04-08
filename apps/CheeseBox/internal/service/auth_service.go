package service

import (
	"context"

	"github.com/cheeseim/cheesebox/internal/domain"
)

type TicketIssuer interface {
	IssueWsTicket(ctx context.Context, accessToken, deviceID, platform string) (domain.WsTicket, error)
}

type AuthConnector interface {
	Connect(ctx context.Context, address, ticket string) error
}

type AuthService struct {
	tickets TicketIssuer
	tcp     AuthConnector
}

func NewAuthService(tickets TicketIssuer, tcp AuthConnector) *AuthService {
	return &AuthService{tickets: tickets, tcp: tcp}
}

func (s *AuthService) Login(ctx context.Context, apiToken, deviceID, platform, tcpAddr string) (domain.WsTicket, error) {
	ticket, err := s.tickets.IssueWsTicket(ctx, apiToken, deviceID, platform)
	if err != nil {
		return domain.WsTicket{}, err
	}
	if err := s.tcp.Connect(ctx, tcpAddr, ticket.Ticket); err != nil {
		return domain.WsTicket{}, err
	}
	return ticket, nil
}

func (s *AuthService) Reconnect(ctx context.Context, apiToken, deviceID, platform, tcpAddr string) (domain.WsTicket, error) {
	return s.Login(ctx, apiToken, deviceID, platform, tcpAddr)
}
