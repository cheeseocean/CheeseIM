package domain

import "testing"

func TestNewConversationRefDirect(t *testing.T) {
	ref := NewConversationRefDirect("user-123")

	if got, want := ref.Kind, ConversationKindDirect; got != want {
		t.Fatalf("Kind = %v, want %v", got, want)
	}
	if got, want := ref.ID, "user-123"; got != want {
		t.Fatalf("ID = %q, want %q", got, want)
	}
}

func TestNewConversationRefGroup(t *testing.T) {
	ref := NewConversationRefGroup("group-456")

	if got, want := ref.Kind, ConversationKindGroup; got != want {
		t.Fatalf("Kind = %v, want %v", got, want)
	}
	if got, want := ref.ID, "group-456"; got != want {
		t.Fatalf("ID = %q, want %q", got, want)
	}
}

func TestMessageItemWithSelfMarksSenderMatch(t *testing.T) {
	item := NewMessageItem("user-123", "hello").WithSelf("user-123")

	if !item.Self {
		t.Fatalf("Self = false, want true")
	}
}

func TestMessageItemWithSelfMarksPeerMismatch(t *testing.T) {
	item := NewMessageItem("user-123", "hello").WithSelf("user-456")

	if item.Self {
		t.Fatalf("Self = true, want false")
	}
}
