package service

import (
	"context"

	"github.com/cheeseim/cheesebox/internal/domain"
)

type RosterClient interface {
	ListFriends(ctx context.Context, accessToken string) ([]domain.FriendSummary, error)
	ListGroups(ctx context.Context, accessToken string) ([]domain.GroupSummary, error)
	ListConversations(ctx context.Context, accessToken string) ([]domain.ConversationSummary, error)
	LoadHistoryPage(ctx context.Context, accessToken, conversationID string, limit int) ([]domain.HistoryMessage, error)
}

type InitialData struct {
	Friends       []domain.FriendSummary
	Groups        []domain.GroupSummary
	Conversations []domain.ConversationSummary
}

type RosterService struct {
	client RosterClient
}

func NewRosterService(client RosterClient) *RosterService {
	return &RosterService{client: client}
}

func (s *RosterService) LoadInitialData(ctx context.Context, accessToken string) (InitialData, error) {
	friends, err := s.client.ListFriends(ctx, accessToken)
	if err != nil {
		return InitialData{}, err
	}
	groups, err := s.client.ListGroups(ctx, accessToken)
	if err != nil {
		return InitialData{}, err
	}
	conversations, err := s.client.ListConversations(ctx, accessToken)
	if err != nil {
		return InitialData{}, err
	}
	return InitialData{
		Friends:       friends,
		Groups:        groups,
		Conversations: conversations,
	}, nil
}

func (s *RosterService) LoadHistory(ctx context.Context, accessToken, conversationID string, limit int) ([]domain.HistoryMessage, error) {
	return s.client.LoadHistoryPage(ctx, accessToken, conversationID, limit)
}
