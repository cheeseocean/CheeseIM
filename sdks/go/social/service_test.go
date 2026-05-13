package social

import (
	"context"
	"testing"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

func TestRosterServiceLoadInitialData(t *testing.T) {
	client := &fakeRosterClient{
		friends:       []types.Friend{{UserID: "user-1"}},
		groups:        []types.Group{{GroupID: "group-1"}},
		conversations: []types.Conversation{{ConversationID: "s:user-1:user-2"}},
		maxSeqs:       []types.ReadSnapshot{{ConversationID: "s:user-1:user-2", MaxSeq: 10}},
		readSnapshots: []types.ReadSnapshot{{ConversationID: "s:user-1:user-2", ReadSeq: 8, MaxSeq: 10, UnreadCount: 2}},
	}
	service := NewRosterService(client)

	data, err := service.LoadInitialData(context.Background(), "token-1")
	if err != nil {
		t.Fatalf("LoadInitialData() error = %v", err)
	}
	if len(data.Friends) != 1 || len(data.Groups) != 1 || len(data.Conversations) != 1 {
		t.Fatalf("unexpected data = %#v", data)
	}
	if data.MaxSeqs["s:user-1:user-2"] != 10 {
		t.Fatalf("max seqs = %#v", data.MaxSeqs)
	}
	if data.ReadSnapshots["s:user-1:user-2"].UnreadCount != 2 {
		t.Fatalf("read snapshots = %#v", data.ReadSnapshots)
	}
}

type fakeRosterClient struct {
	friends       []types.Friend
	groups        []types.Group
	conversations []types.Conversation
	maxSeqs       []types.ReadSnapshot
	readSnapshots []types.ReadSnapshot
	pulled        []types.PulledConversationMessages
	err           error
}

func (f *fakeRosterClient) ListFriends(context.Context, string) ([]types.Friend, error) {
	return f.friends, f.err
}

func (f *fakeRosterClient) ListGroups(context.Context, string) ([]types.Group, error) {
	return f.groups, f.err
}

func (f *fakeRosterClient) ListConversations(context.Context, string) ([]types.Conversation, error) {
	return f.conversations, f.err
}

func (f *fakeRosterClient) GetConversationMaxSeqs(context.Context, string) ([]types.ReadSnapshot, error) {
	return f.maxSeqs, f.err
}

func (f *fakeRosterClient) GetConversationReadSnapshots(context.Context, string) ([]types.ReadSnapshot, error) {
	return f.readSnapshots, f.err
}

func (f *fakeRosterClient) PullMessages(context.Context, string, []types.SeqRange, int64) ([]types.PulledConversationMessages, error) {
	return f.pulled, f.err
}

func (f *fakeRosterClient) AckReadSeq(context.Context, string, string, int64) error {
	return f.err
}
