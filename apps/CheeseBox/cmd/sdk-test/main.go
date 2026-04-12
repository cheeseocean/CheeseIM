// sdk-test: Direct SDK test tool for CheeseIM send/recv
// Usage: go run ./cmd/sdk-test <userID> <password> <conversationID>
// Example: go run ./cmd/sdk-test user1 pass123 s:user1:user2
package main

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"

	sdkclient "github.com/cheeseim/cheeseim-go-sdk/client"
	"github.com/cheeseim/cheeseim-go-sdk/types"
)

const (
	apiBaseURL = "http://127.0.0.1:18085"
	tcpAddr    = "127.0.0.1:5148"
	deviceID   = "sdk-test-cli"
	platform   = "desktop"
)

type msgEntry struct {
	ts      time.Time
	dir     string // ">>>" sent, "<<<" recv, "ACK" ack
	content string
	detail  string
}

func main() {
	if len(os.Args) < 4 {
		fmt.Println("Usage: go run ./cmd/sdk-test <userID> <password> <conversationID>")
		fmt.Println("  conversationID format:")
		fmt.Println("    Single chat: s:<userID1>:<userID2> (sorted)")
		fmt.Println("    Group chat:  c2:<groupID>")
		fmt.Println("    C2C:         g:<groupID>")
		fmt.Println()
		fmt.Println("Examples:")
		fmt.Println("  go run ./cmd/sdk-test user1 pass123 s:user1:user2")
		fmt.Println("  go run ./cmd/sdk-test user1 pass123 c2:group1")
		os.Exit(1)
	}

	userID := os.Args[1]
	password := os.Args[2]
	convoID := os.Args[3]

	fmt.Println("=== CheeseIM SDK Direct Test ===")
	fmt.Printf("API:  %s\n", apiBaseURL)
	fmt.Printf("TCP:  %s\n", tcpAddr)
	fmt.Printf("User: %s\n", userID)
	fmt.Printf("Conv: %s\n\n", convoID)

	// Create SDK client
	client := sdkclient.New(sdkclient.Config{
		APIBaseURL: apiBaseURL,
		TCPAddr:    tcpAddr,
		DeviceID:   deviceID,
		Platform:   platform,
		Timeout:    10 * time.Second,
	})

	// Login
	fmt.Println("[*] Logging in...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	bootstrap, err := client.Login(ctx, userID, password)
	cancel()
	if err != nil {
		fmt.Fprintf(os.Stderr, "[!] Login failed: %v\n", err)
		os.Exit(1)
	}

	actualUserID := client.CurrentUserID()
	fmt.Printf("[+] Login OK! UserID=%s\n", actualUserID)
	fmt.Printf("    Friends: %d, Groups: %d, Conversations: %d\n\n",
		len(bootstrap.Friends), len(bootstrap.Groups), len(bootstrap.Conversations))

	// Start event listener
	var mu sync.Mutex
	var messages []msgEntry
	done := make(chan struct{})

	go func() {
		fmt.Println("[*] Listening for events...")
		events := client.Events()
		for event := range events {
			handleEvent(&mu, &messages, actualUserID, event)
		}
		fmt.Println("[*] Event listener stopped")
		close(done)
	}()

	// Interactive loop
	fmt.Println("\n=== Interactive Mode ===")
	fmt.Println("Type a message and press Enter to send")
	fmt.Println("Type 'quit' or 'q' to exit")
	fmt.Println()

	scanner := bufio.NewScanner(os.Stdin)
	for {
		fmt.Print("> ")
		if !scanner.Scan() {
			break
		}
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		if line == "quit" || line == "q" {
			break
		}

		// Send message (requestID must be <= 16 bytes per TCP protocol)
		requestID := fmt.Sprintf("c%015x", time.Now().UnixNano()&0x0fffffffffffffff)
		fmt.Printf("\n[>>>] Sending: %q (reqID=%s)\n", line, requestID)

		result, err := client.SendText(requestID, convoID, line)
		
		if err != nil {
			fmt.Printf("[!!!] Send ERROR: %v\n\n", err)
			addMessage(&mu, &messages, msgEntry{
				ts:      time.Now(),
				dir:     "!!!",
				content: fmt.Sprintf("SEND ERROR: %v", err),
			})
			continue
		}

		fmt.Printf("[ACK] Send OK: clientMsgID=%s, serverMsgID=%s, returnedContent=%q\n\n",
			result.ClientMsgID, result.ServerMsgID, string(result.Content))
		
		addMessage(&mu, &messages, msgEntry{
			ts:      time.Now(),
			dir:     ">>>",
			content: line,
			detail:  fmt.Sprintf("reqID=%s clientMsgID=%s", requestID, result.ClientMsgID),
		})

		// Print recent messages
		printMessages(&mu, &messages, 10)
	}

	fmt.Println("\n[*] Shutting down...")
	<-done

	fmt.Println("\n=== Session Summary ===")
	printMessages(&mu, &messages, -1)
	fmt.Printf("\nTotal: %d sent, %d received\n",
		countDir(messages, ">>>"), countDir(messages, "<<<"))
}

func addMessage(mu *sync.Mutex, msgs *[]msgEntry, msg msgEntry) {
	mu.Lock()
	defer mu.Unlock()
	*msgs = append(*msgs, msg)
}

func printMessages(mu *sync.Mutex, msgs *[]msgEntry, lastN int) {
	mu.Lock()
	defer mu.Unlock()

	start := 0
	if lastN > 0 && len(*msgs) > lastN {
		start = len(*msgs) - lastN
	}

	for i := start; i < len(*msgs); i++ {
		e := (*msgs)[i]
		fmt.Printf("[%s] %s %s\n", e.ts.Format("15:04:05"), e.dir, e.content)
		if e.detail != "" {
			fmt.Printf("         %s\n", e.detail)
		}
	}
}

func countDir(entries []msgEntry, dir string) int {
	n := 0
	for _, e := range entries {
		if e.dir == dir {
			n++
		}
	}
	return n
}

func handleEvent(mu *sync.Mutex, msgs *[]msgEntry, myUserID string, event types.Event) {
	now := time.Now()
	
	switch event.Kind {
	case types.EventKindConnected:
		fmt.Printf("[CON] Connected\n")
		addMessage(mu, msgs, msgEntry{ts: now, dir: "CON", content: "Connected"})
		
	case types.EventKindAck:
		fmt.Printf("[ACK] Ack received (req=%s)\n", event.RequestID)
		
	case types.EventKindRealtime:
		if event.Message == nil {
			return
		}
		content := string(event.Message.Content)
		sender := event.Message.SenderID
		if sender == myUserID {
			sender = "me"
		}
		
		fmt.Printf("[<<<] [%s] %s (seq=%d, conv=%s)\n",
			sender, content, event.Message.Sequence, event.ConversationID)
		fmt.Printf("       clientMsgID=%s serverMsgID=%s\n\n",
			event.Message.ClientMsgID, event.Message.ServerMsgID)
		
		addMessage(mu, msgs, msgEntry{
			ts:      now,
			dir:     "<<<",
			content: fmt.Sprintf("[%s] %s", sender, content),
			detail:  fmt.Sprintf("seq=%d conv=%s clientMsgID=%s",
				event.Message.Sequence, event.ConversationID, event.Message.ClientMsgID),
		})
		
	case types.EventKindError:
		fmt.Printf("[ERR] %v\n", event.Err)
		addMessage(mu, msgs, msgEntry{ts: now, dir: "ERR", content: fmt.Sprintf("ERROR: %v", event.Err)})
		
	case types.EventKindDisconnected:
		fmt.Printf("[DIS] Disconnected\n")
		addMessage(mu, msgs, msgEntry{ts: now, dir: "DIS", content: "Disconnected"})
		
	case types.EventKindSyncStarted:
		// Silent
		
	case types.EventKindSyncCompleted:
		fmt.Printf("[SYNC] Sync completed (conv=%s)\n", event.ConversationID)
		
	case types.EventKindGapRepaired:
		fmt.Printf("[GAP] Gap repaired (conv=%s)\n", event.ConversationID)
		
	case types.EventKindReadUpdated:
		// Silent
		
	default:
		fmt.Printf("[???] Unknown event: %s\n", event.Kind)
	}
}
