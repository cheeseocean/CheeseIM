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

func TestPersistedStoreControlEventCursorSurvivesReloadAndNeverRegresses(t *testing.T) {
	dir := t.TempDir()
	first, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("NewPersistedStore() error = %v", err)
	}
	if err := first.SetControlEventCursor(42); err != nil {
		t.Fatalf("SetControlEventCursor() error = %v", err)
	}
	if err := first.SetControlEventCursor(41); err != nil {
		t.Fatalf("regressing SetControlEventCursor() error = %v", err)
	}

	reloaded, err := NewPersistedStore(dir)
	if err != nil {
		t.Fatalf("reload NewPersistedStore() error = %v", err)
	}
	if got := reloaded.GetControlEventCursor(); got != 42 {
		t.Fatalf("control cursor = %d, want 42", got)
	}
}
