package service

import (
	"context"
	"testing"
)

func TestContactServiceAddFriend(t *testing.T) {
	requester := &fakeFriendRequester{}
	service := NewContactService(requester)

	if err := service.AddFriend(context.Background(), "token-1", "user-2", "hi"); err != nil {
		t.Fatalf("AddFriend() error = %v", err)
	}
	if requester.friendUserID != "user-2" || requester.message != "hi" || requester.accessToken != "token-1" {
		t.Fatalf("unexpected requester state = %#v", requester)
	}
}

type fakeFriendRequester struct {
	accessToken  string
	friendUserID string
	message      string
	err          error
}

func (f *fakeFriendRequester) AddFriend(_ context.Context, accessToken, friendUserID, message string) error {
	f.accessToken = accessToken
	f.friendUserID = friendUserID
	f.message = message
	return f.err
}
