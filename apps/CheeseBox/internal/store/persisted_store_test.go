package store

import (
	"path/filepath"
	"testing"
)

func TestNewPersistedStoreForUserUsesSeparateNamespacePerUser(t *testing.T) {
	baseDir := t.TempDir()

	first, err := NewPersistedStoreForUser(baseDir, "user-1")
	if err != nil {
		t.Fatalf("NewPersistedStoreForUser() error = %v", err)
	}
	second, err := NewPersistedStoreForUser(baseDir, "user/2")
	if err != nil {
		t.Fatalf("NewPersistedStoreForUser() error = %v", err)
	}

	if first.path() == second.path() {
		t.Fatalf("paths should differ, got %q", first.path())
	}
	if filepath.Dir(filepath.Dir(first.path())) != filepath.Join(baseDir, "users") {
		t.Fatalf("first path = %q, want under users namespace", first.path())
	}
}
