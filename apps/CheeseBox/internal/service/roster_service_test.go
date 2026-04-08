package service

import (
	"context"
	"testing"

	"github.com/cheeseim/cheesebox/internal/domain"
)

func TestRosterServiceLoadInitialData(t *testing.T) {
	client := &fakeRosterClient{
		friends:       []domain.FriendSummary{{UserID: "user-1"}},
		groups:        []domain.GroupSummary{{GroupID: "group-1"}},
		conversations: []domain.ConversationSummary{{ConversationID: "c1:user-1:user-2"}},
	}
	service := NewRosterService(client)

	data, err := service.LoadInitialData(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("LoadInitialData() error = %v", err)
	}
	if len(data.Friends) != 1 || len(data.Groups) != 1 || len(data.Conversations) != 1 {
		t.Fatalf("unexpected data = %#v", data)
	}
}

type fakeRosterClient struct {
	friends       []domain.FriendSummary
	groups        []domain.GroupSummary
	conversations []domain.ConversationSummary
	history       []domain.HistoryMessage
	err           error
}

func (f *fakeRosterClient) ListFriends(context.Context, string) ([]domain.FriendSummary, error) {
	return f.friends, f.err
}

func (f *fakeRosterClient) ListGroups(context.Context, string) ([]domain.GroupSummary, error) {
	return f.groups, f.err
}

func (f *fakeRosterClient) ListConversations(context.Context, string) ([]domain.ConversationSummary, error) {
	return f.conversations, f.err
}

func (f *fakeRosterClient) LoadHistoryPage(context.Context, string, string, int) ([]domain.HistoryMessage, error) {
	return f.history, f.err
}
