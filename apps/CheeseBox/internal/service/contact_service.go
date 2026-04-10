package service

import "context"

type FriendRequester interface {
	AddFriend(ctx context.Context, accessToken, friendUserID, message string) error
}

type ContactService struct {
	requester FriendRequester
}

func NewContactService(requester FriendRequester) *ContactService {
	return &ContactService{requester: requester}
}

func (s *ContactService) AddFriend(ctx context.Context, accessToken, friendUserID, message string) error {
	return s.requester.AddFriend(ctx, accessToken, friendUserID, message)
}
