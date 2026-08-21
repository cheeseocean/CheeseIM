package store

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"

	sdktypes "github.com/cheeseim/cheeseim-go-sdk/types"
)

// 本地存储路径
const storeFileName = "cheesebox_store.json"

// PersistedStore 持久化存储
type PersistedStore struct {
	mu            sync.RWMutex
	dir           string
	messages      map[string][]MessageRecord
	conversations map[string]ConversationRecord
	cursor        sdktypes.ConversationSyncCursor
}

// MessageRecord 消息记录
type MessageRecord struct {
	ID             string `json:"id"`
	ConversationID string `json:"conversation_id"`
	Sequence       int64  `json:"seq"`
	ClientMsgID    string `json:"client_msg_id"`
	ServerMsgID    string `json:"server_msg_id"`
	SenderID       string `json:"sender_id"`
	SenderLabel    string `json:"sender_label"`
	Content        string `json:"content"`
	Self           bool   `json:"self"`
	SendTime       int64  `json:"send_time"`
	CreateTime     int64  `json:"create_time"`
	DeliveryState string `json:"delivery_state,omitempty"`
	Revoked       bool   `json:"revoked,omitempty"`
	RevokedBy     string `json:"revoked_by,omitempty"`
	RevokedAt     int64  `json:"revoked_at,omitempty"`
	MutationVersion int64 `json:"mutation_version,omitempty"`
}

// ConversationRecord 会话记录
type ConversationRecord struct {
	ConversationID     string `json:"conversation_id"`
	Title              string `json:"title"`
	LastMessagePreview string `json:"last_message_preview"`
	LastMessageTime    int64  `json:"last_message_time"`
	UnreadCount        int    `json:"unread_count"`
}

// NewPersistedStore 创建持久化存储
func NewPersistedStore(dir string) (*PersistedStore, error) {
	if dir == "" {
		defaultDir, err := defaultStoreDir()
		if err != nil {
			return nil, err
		}
		dir = defaultDir
	}
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, fmt.Errorf("create store dir failed: %w", err)
	}
	store := &PersistedStore{
		dir:           dir,
		messages:      make(map[string][]MessageRecord),
		conversations: make(map[string]ConversationRecord),
	}
	if err := store.load(); err != nil {
		// 文件不存在或解析失败，使用空存储
	}
	return store, nil
}

// NewPersistedStoreForUser 创建用户维度持久化存储，避免多个账号共用同一个本地文件。
func NewPersistedStoreForUser(baseDir, userID string) (*PersistedStore, error) {
	if userID == "" {
		return nil, fmt.Errorf("userID required")
	}
	if baseDir == "" {
		defaultDir, err := defaultStoreDir()
		if err != nil {
			return nil, err
		}
		baseDir = defaultDir
	}
	encodedUserID := base64.RawURLEncoding.EncodeToString([]byte(userID))
	return NewPersistedStore(filepath.Join(baseDir, "users", encodedUserID))
}

func defaultStoreDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("get home dir failed: %w", err)
	}
	return filepath.Join(home, ".cheesebox"), nil
}

// path 返回存储文件路径
func (s *PersistedStore) path() string {
	return filepath.Join(s.dir, storeFileName)
}

// load 从磁盘加载数据
func (s *PersistedStore) load() error {
	data, err := os.ReadFile(s.path())
	if err != nil {
		return err
	}
	var stored storedData
	if err := json.Unmarshal(data, &stored); err != nil {
		return err
	}
	s.messages = stored.Messages
	s.conversations = stored.Conversations
	s.cursor = stored.ConversationCursor
	return nil
}

// save 持久化数据到磁盘
func (s *PersistedStore) save() error {
	data := storedData{
		Messages:           s.messages,
		Conversations:      s.conversations,
		ConversationCursor: s.cursor,
	}
	encoded, err := json.Marshal(data)
	if err != nil {
		return err
	}
	return os.WriteFile(s.path(), encoded, 0644)
}

// storedData 存储数据结构
type storedData struct {
	Messages           map[string][]MessageRecord      `json:"messages"`
	Conversations      map[string]ConversationRecord   `json:"conversations"`
	ConversationCursor sdktypes.ConversationSyncCursor `json:"conversation_cursor"`
}

// GetMessages 获取会话消息
func (s *PersistedStore) GetMessages(conversationID string) []MessageRecord {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return append([]MessageRecord(nil), s.messages[conversationID]...)
}

// AppendMessage 追加消息
func (s *PersistedStore) AppendMessage(conversationID string, msg MessageRecord) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.messages[conversationID] = append(s.messages[conversationID], msg)
	// 异步保存，避免阻塞
	go s.save()
}

// SetMessages 设置会话消息（覆盖）
func (s *PersistedStore) SetMessages(conversationID string, msgs []MessageRecord) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.messages[conversationID] = append([]MessageRecord(nil), msgs...)
	go s.save()
}

// GetConversations 获取所有会话
func (s *PersistedStore) GetConversations() map[string]ConversationRecord {
	s.mu.RLock()
	defer s.mu.RUnlock()
	result := make(map[string]ConversationRecord, len(s.conversations))
	for k, v := range s.conversations {
		result[k] = v
	}
	return result
}

// UpsertConversation 更新或插入会话
func (s *PersistedStore) UpsertConversation(conv ConversationRecord) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.conversations[conv.ConversationID] = conv
	go s.save()
}

// GetConversationCursor 获取本地会话元数据同步游标。
func (s *PersistedStore) GetConversationCursor() sdktypes.ConversationSyncCursor {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cursor
}

// SetConversationCursor 保存本地会话元数据同步游标。
func (s *PersistedStore) SetConversationCursor(cursor sdktypes.ConversationSyncCursor) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.cursor = cursor
	go s.save()
}

// ClearMessages 清除会话消息
func (s *PersistedStore) ClearMessages(conversationID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.messages, conversationID)
	go s.save()
}

// Clear 清除所有数据
func (s *PersistedStore) Clear() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.messages = make(map[string][]MessageRecord)
	s.conversations = make(map[string]ConversationRecord)
	s.cursor = sdktypes.ConversationSyncCursor{}
	go s.save()
}
