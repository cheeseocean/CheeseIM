package social

import (
	"context"

	"github.com/cheeseim/cheeseim-go-sdk/types"
)

type RosterClient interface {
	ListFriends(ctx context.Context, accessToken string) ([]types.Friend, error)
	ListGroups(ctx context.Context, accessToken string) ([]types.Group, error)
	ListConversations(ctx context.Context, accessToken string) ([]types.Conversation, error)
	GetConversationMaxSeqs(ctx context.Context, accessToken string) ([]types.ReadSnapshot, error)
	GetConversationReadSnapshots(ctx context.Context, accessToken string) ([]types.ReadSnapshot, error)
	SyncConversations(ctx context.Context, accessToken string, cursor types.ConversationSyncCursor) (types.ConversationSyncResult, error)
	PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limitPerConversation int64) ([]types.PulledConversationMessages, error)
	AckReadSeq(ctx context.Context, accessToken, conversationID string, readSeq int64) error
	DeleteConversation(ctx context.Context, accessToken, conversationID string) error
}

type BootstrapData = types.BootstrapData

type RosterService struct {
	client RosterClient
}

func NewRosterService(client RosterClient) *RosterService {
	return &RosterService{client: client}
}

func (s *RosterService) LoadInitialData(ctx context.Context, accessToken string) (BootstrapData, error) {
	friends, err := s.client.ListFriends(ctx, accessToken)
	if err != nil {
		return BootstrapData{}, err
	}
	groups, err := s.client.ListGroups(ctx, accessToken)
	if err != nil {
		return BootstrapData{}, err
	}
	conversations, err := s.client.ListConversations(ctx, accessToken)
	if err != nil {
		return BootstrapData{}, err
	}
	maxSeqSnapshots, err := s.client.GetConversationMaxSeqs(ctx, accessToken)
	if err != nil {
		return BootstrapData{}, err
	}
	readSnapshots, err := s.client.GetConversationReadSnapshots(ctx, accessToken)
	if err != nil {
		return BootstrapData{}, err
	}
	maxSeqs := make(map[string]int64, len(maxSeqSnapshots))
	for _, item := range maxSeqSnapshots {
		maxSeqs[item.ConversationID] = item.MaxSeq
	}
	readMap := make(map[string]types.ReadSnapshot, len(readSnapshots))
	for _, item := range readSnapshots {
		readMap[item.ConversationID] = item
	}
	return BootstrapData{
		Friends:       friends,
		Groups:        groups,
		Conversations: conversations,
		MaxSeqs:       maxSeqs,
		ReadSnapshots: readMap,
	}, nil
}

func (s *RosterService) PullMessages(ctx context.Context, accessToken string, ranges []types.SeqRange, limit int64) ([]types.PulledConversationMessages, error) {
	return s.client.PullMessages(ctx, accessToken, ranges, limit)
}

func (s *RosterService) SyncConversations(ctx context.Context, accessToken string, cursor types.ConversationSyncCursor) (types.ConversationSyncResult, error) {
	return s.client.SyncConversations(ctx, accessToken, cursor)
}

func (s *RosterService) AckReadSeq(ctx context.Context, accessToken, conversationID string, readSeq int64) error {
	return s.client.AckReadSeq(ctx, accessToken, conversationID, readSeq)
}

func (s *RosterService) DeleteConversation(ctx context.Context, accessToken, conversationID string) error {
	return s.client.DeleteConversation(ctx, accessToken, conversationID)
}
