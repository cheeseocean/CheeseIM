package integration_test

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"testing"
	"time"

	pb "github.com/cheeseim/cheeseim-go-sdk/proto"
	"github.com/cheeseim/cheeseim-go-sdk/transport/httpapi"
	"github.com/cheeseim/cheeseim-go-sdk/transport/tcpim"
	"github.com/cheeseim/cheeseim-go-sdk/types"
)

const (
	defaultAPIBaseURL = "http://127.0.0.1:18079"
	defaultTCPAddr    = "127.0.0.1:5148"
	assertionIssuer   = "cheeseim-account"
	assertionAudience = "cheeseim-im"
)

func TestDirectMessageLifecycle(t *testing.T) {
	if os.Getenv("CHEESEIM_E2E") != "1" {
		t.Skip("set CHEESEIM_E2E=1 to run against a real CheeseIM server")
	}
	secret := os.Getenv("CHEESEIM_LOGIN_ASSERTION_SECRET")
	if len([]byte(secret)) < 32 {
		t.Fatal("CHEESEIM_LOGIN_ASSERTION_SECRET must contain at least 32 bytes")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	api := httpapi.New(envOrDefault("CHEESEIM_E2E_API_BASE_URL", defaultAPIBaseURL), 5*time.Second)
	tcpAddress := envOrDefault("CHEESEIM_E2E_TCP_ADDR", defaultTCPAddr)
	runID := uniqueID(t)
	alice := loginClient(t, ctx, api, tcpAddress, secret, "e2e-alice-"+runID)
	bob := loginClient(t, ctx, api, tcpAddress, secret, "e2e-bob-"+runID)
	t.Cleanup(func() {
		_ = alice.tcp.Close()
		_ = bob.tcp.Close()
	})

	if err := api.AddFriend(ctx, alice.accessToken, bob.userID, "cheeseim e2e"); err != nil {
		t.Fatalf("send friend request: %v", err)
	}
	awaitIncomingFriendRequest(t, ctx, api, bob.accessToken, alice.userID)
	if err := api.AcceptFriendRequest(ctx, bob.accessToken, alice.userID); err != nil {
		t.Fatalf("accept friend request: %v", err)
	}
	awaitFriendship(t, ctx, api, alice.accessToken, bob.userID)
	awaitFriendship(t, ctx, api, bob.accessToken, alice.userID)

	conversationID := directConversationID(alice.userID, bob.userID)
	requestID := "e2e-" + runID[:12]
	content := []byte("cheeseim-e2e-" + runID)
	typingOpID := "typing-" + runID[:9]
	if err := alice.tcp.SendTyping(typingOpID, &pb.ProtoChatTypingCommand{
		ConversationId: conversationID, Action: int32(types.TypingActionStart), TtlSeconds: 4,
	}); err != nil {
		t.Fatalf("send typing START: %v", err)
	}
	typing := awaitTyping(t, ctx, bob.tcp.Events(), conversationID, alice.userID, types.TypingActionStart)
	if typing.GetExpiresAt() <= time.Now().UnixMilli() {
		t.Fatalf("typing expiry = %d, want future timestamp", typing.GetExpiresAt())
	}
	if err := alice.tcp.SendChatMessage(requestID, &pb.ProtoMessage{
		ClientMsgId: requestID,
		ReceiverId:  bob.userID,
		ContentType: 101,
		ChatType:    1,
		Content:     content,
		SendTime:    time.Now().UnixMilli(),
	}); err != nil {
		t.Fatalf("send chat message: %v", err)
	}

	ack := awaitAck(t, ctx, alice.tcp.Events(), requestID)
	if ack.GetServerMsgId() == "" || ack.GetClientMsgId() != requestID {
		t.Fatalf("invalid send ack: %#v", ack)
	}
	if ack.GetAcceptedState() != pb.ProtoChatSendAcceptedState_CHAT_SEND_BROKER_ACCEPTED {
		t.Fatalf("accepted state = %s", ack.GetAcceptedState())
	}

	received := awaitMessage(t, ctx, bob.tcp.Events(), requestID)
	if received.GetServerMsgId() != ack.GetServerMsgId() || received.GetSenderId() != alice.userID || received.GetReceiverId() != bob.userID {
		t.Fatalf("realtime message does not match ack/participants: %#v", received)
	}
	if string(received.GetContent()) != string(content) || received.GetSeq() <= 0 {
		t.Fatalf("invalid realtime content or seq: %#v", received)
	}
	stopOpID := "stop-" + runID[:11]
	if err := alice.tcp.SendTyping(stopOpID, &pb.ProtoChatTypingCommand{
		ConversationId: conversationID, Action: int32(types.TypingActionStop), TtlSeconds: 4,
	}); err != nil {
		t.Fatalf("send typing STOP: %v", err)
	}
	awaitTyping(t, ctx, bob.tcp.Events(), conversationID, alice.userID, types.TypingActionStop)
	deliveryOpID := "delivery-" + runID[:7]
	if err := bob.tcp.AckDelivery(deliveryOpID, &pb.ProtoChatDeliveryAckCommand{
		ConversationId:  conversationID,
		MaxDeliveredSeq: received.GetSeq(),
		DeviceId:        bob.deviceID,
		OpId:            deliveryOpID,
	}); err != nil {
		t.Fatalf("ack delivery: %v", err)
	}
	delivery := awaitDelivery(t, ctx, alice.tcp.Events(), conversationID, received.GetSeq())
	if delivery.GetRecipientId() != bob.userID {
		t.Fatalf("delivery recipient = %q, want %q", delivery.GetRecipientId(), bob.userID)
	}

	persisted := awaitHistory(t, ctx, api, bob.accessToken, conversationID, received.GetSeq(), ack.GetServerMsgId())
	if string(persisted.Content) != string(content) {
		t.Fatalf("history content = %q, want %q", persisted.Content, content)
	}
	if err := api.AckReadSeq(ctx, bob.accessToken, conversationID, received.GetSeq()); err != nil {
		t.Fatalf("ack read seq: %v", err)
	}
	readNotify := awaitReadNotify(t, ctx, alice.tcp.Events(), conversationID, received.GetSeq())
	if readNotify.GetReaderId() != bob.userID {
		t.Fatalf("read reader = %q, want %q", readNotify.GetReaderId(), bob.userID)
	}
	awaitReadSeq(t, ctx, api, bob.accessToken, conversationID, received.GetSeq())
	revokeOpID := "revoke-" + runID[:9]
	if err := alice.tcp.RevokeMessage(revokeOpID, &pb.ProtoChatRevokeCommand{
		ConversationId: conversationID,
		ServerMsgId:    received.GetServerMsgId(),
		OpId:           revokeOpID,
		Reason:         "e2e cleanup",
	}); err != nil {
		t.Fatalf("revoke message: %v", err)
	}
	revoke := awaitRevoke(t, ctx, bob.tcp.Events(), conversationID, received.GetServerMsgId())
	if revoke.GetOperatorUserId() != alice.userID || revoke.GetMutationVersion() <= 0 {
		t.Fatalf("invalid revoke notify: %#v", revoke)
	}
}

type connectedClient struct {
	userID      string
	accessToken string
	deviceID    string
	tcp         *tcpim.Client
}

func loginClient(t *testing.T, ctx context.Context, api *httpapi.Client, tcpAddress, secret, userID string) connectedClient {
	t.Helper()
	deviceID := "device-" + userID
	assertion := signAssertion(t, userID, secret)
	accessToken, err := api.Login(ctx, userID, assertion, 2, deviceID, "cheeseim-e2e")
	if err != nil {
		t.Fatalf("login %s: %v", userID, err)
	}
	ticket, err := api.IssueWsTicket(ctx, accessToken, deviceID, "cli")
	if err != nil {
		t.Fatalf("issue ticket %s: %v", userID, err)
	}
	tcpClient := tcpim.NewClient(nil, 5*time.Second)
	authenticatedUserID, err := tcpClient.Connect(ctx, tcpAddress, ticket.Ticket)
	if err != nil {
		t.Fatalf("tcp auth %s: %v", userID, err)
	}
	if authenticatedUserID != userID {
		t.Fatalf("authenticated user = %q, want %q", authenticatedUserID, userID)
	}
	return connectedClient{userID: userID, accessToken: accessToken, deviceID: deviceID, tcp: tcpClient}
}

func awaitDelivery(t *testing.T, ctx context.Context, events <-chan tcpim.Event, conversationID string, seq int64) *pb.ProtoChatDeliveryNotify {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait delivery %s/%d: %v", conversationID, seq, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for delivery: %v", event.Err)
			}
			if event.Kind == tcpim.EventDelivery && event.Delivery.GetConversationId() == conversationID &&
				event.Delivery.GetDeliveredSeq() >= seq {
				return event.Delivery
			}
		}
	}
}

func awaitReadNotify(t *testing.T, ctx context.Context, events <-chan tcpim.Event, conversationID string, seq int64) *pb.ProtoChatReadNotify {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait read notify %s/%d: %v", conversationID, seq, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for read notify: %v", event.Err)
			}
			if event.Kind == tcpim.EventRead && event.Read.GetConversationId() == conversationID &&
				event.Read.GetReadSeq() >= seq {
				return event.Read
			}
		}
	}
}

func awaitRevoke(t *testing.T, ctx context.Context, events <-chan tcpim.Event, conversationID, serverMsgID string) *pb.ProtoChatRevokeNotify {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait revoke %s/%s: %v", conversationID, serverMsgID, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for revoke: %v", event.Err)
			}
			if event.Kind == tcpim.EventRevoke && event.Revoke.GetConversationId() == conversationID &&
				event.Revoke.GetServerMsgId() == serverMsgID {
				return event.Revoke
			}
		}
	}
}

func awaitTyping(t *testing.T, ctx context.Context, events <-chan tcpim.Event, conversationID, senderID string, action types.TypingAction) *pb.ProtoChatTypingNotify {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait typing %s/%s/%d: %v", conversationID, senderID, action, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for typing: %v", event.Err)
			}
			if event.Kind == tcpim.EventTyping && event.Typing.GetConversationId() == conversationID &&
				event.Typing.GetSenderId() == senderID && event.Typing.GetAction() == int32(action) {
				return event.Typing
			}
		}
	}
}

func awaitIncomingFriendRequest(t *testing.T, ctx context.Context, api *httpapi.Client, token, fromUserID string) {
	t.Helper()
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		requests, err := api.ListIncomingFriendRequests(ctx, token)
		if err == nil {
			for _, request := range requests {
				if request.FromUserID == fromUserID && request.Status == types.FriendRequestPending {
					return
				}
			}
		}
		select {
		case <-ctx.Done():
			t.Fatalf("wait incoming friend request from %s: last error: %v", fromUserID, err)
		case <-ticker.C:
		}
	}
}

func awaitFriendship(t *testing.T, ctx context.Context, api *httpapi.Client, token, friendUserID string) {
	t.Helper()
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		friends, err := api.ListFriends(ctx, token)
		if err == nil {
			for _, friend := range friends {
				if friend.UserID == friendUserID {
					return
				}
			}
		}
		select {
		case <-ctx.Done():
			t.Fatalf("wait friendship with %s: last error: %v", friendUserID, err)
		case <-ticker.C:
		}
	}
}

func awaitAck(t *testing.T, ctx context.Context, events <-chan tcpim.Event, requestID string) *pb.ProtoChatSendAck {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait send ack %s: %v", requestID, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for ack: %v", event.Err)
			}
			if event.Kind == tcpim.EventAck && event.RequestID == requestID {
				return event.Ack
			}
		}
	}
}

func awaitMessage(t *testing.T, ctx context.Context, events <-chan tcpim.Event, requestID string) *pb.ProtoMessage {
	t.Helper()
	for {
		select {
		case <-ctx.Done():
			t.Fatalf("wait realtime message %s: %v", requestID, ctx.Err())
		case event := <-events:
			if event.Kind == tcpim.EventError {
				t.Fatalf("tcp error while waiting for message: %v", event.Err)
			}
			if event.Kind == tcpim.EventMessage && event.Message.GetClientMsgId() == requestID {
				return event.Message
			}
		}
	}
}

func awaitHistory(t *testing.T, ctx context.Context, api *httpapi.Client, token, conversationID string, seq int64, serverMsgID string) types.Message {
	t.Helper()
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		items, err := api.PullMessages(ctx, token, []types.SeqRange{{
			ConversationID: conversationID,
			BeginSeq:       seq,
			EndSeq:         seq,
		}}, 10)
		if err == nil {
			for _, conversation := range items {
				for _, message := range conversation.Messages {
					if message.ServerMsgID == serverMsgID {
						return message
					}
				}
			}
		}
		select {
		case <-ctx.Done():
			t.Fatalf("wait history message %s: last error: %v", serverMsgID, err)
		case <-ticker.C:
		}
	}
}

func awaitReadSeq(t *testing.T, ctx context.Context, api *httpapi.Client, token, conversationID string, want int64) {
	t.Helper()
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		snapshots, err := api.GetConversationReadSnapshots(ctx, token)
		if err == nil {
			for _, snapshot := range snapshots {
				if snapshot.ConversationID == conversationID && snapshot.ReadSeq >= want {
					return
				}
			}
		}
		select {
		case <-ctx.Done():
			t.Fatalf("wait read seq %s/%d: last error: %v", conversationID, want, err)
		case <-ticker.C:
		}
	}
}

func directConversationID(left, right string) string {
	users := []string{left, right}
	sort.Strings(users)
	return "s:" + users[0] + ":" + users[1]
}

func signAssertion(t *testing.T, userID, secret string) string {
	t.Helper()
	now := time.Now()
	header := mustJSON(t, map[string]string{"alg": "HS256", "typ": "JWT"})
	claims := mustJSON(t, map[string]any{
		"sub": userID,
		"iss": assertionIssuer,
		"aud": assertionAudience,
		"jti": uniqueID(t),
		"iat": now.Unix(),
		"exp": now.Add(time.Minute).Unix(),
	})
	unsigned := base64.RawURLEncoding.EncodeToString(header) + "." + base64.RawURLEncoding.EncodeToString(claims)
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(unsigned))
	return unsigned + "." + base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func mustJSON(t *testing.T, value any) []byte {
	t.Helper()
	payload, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return payload
}

func uniqueID(t *testing.T) string {
	t.Helper()
	value := make([]byte, 8)
	if _, err := rand.Read(value); err != nil {
		t.Fatal(err)
	}
	return hex.EncodeToString(value)
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func Example_e2eEnvironment() {
	fmt.Println("CHEESEIM_E2E=1 CHEESEIM_LOGIN_ASSERTION_SECRET=<32+ bytes> go test ./integration -v")
	// Output: CHEESEIM_E2E=1 CHEESEIM_LOGIN_ASSERTION_SECRET=<32+ bytes> go test ./integration -v
}
