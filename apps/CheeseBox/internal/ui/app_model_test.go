package ui

import (
	"strings"
	"testing"

	tea "github.com/charmbracelet/bubbletea"

	"github.com/cheeseim/cheesebox/internal/config"
	"github.com/cheeseim/cheesebox/internal/domain"
	"github.com/cheeseim/cheesebox/internal/store"
)

func TestAppModelNavigationSwitching(t *testing.T) {
	appStore := store.New()
	model := NewAppModel(appStore, config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("f")})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeyFriends {
		t.Fatalf("nav = %q, want friends", appStore.ActiveNav)
	}

	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("g")})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeyGroups {
		t.Fatalf("nav = %q, want groups", appStore.ActiveNav)
	}

	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyDown})
	model = updated.(AppModel)
	if appStore.ActiveNav != domain.NavKeySettings {
		t.Fatalf("nav = %q, want settings", appStore.ActiveNav)
	}
}

func TestAppModelTabAndEscFocus(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(AppModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyEsc})
	model = updated.(AppModel)
	if model.Focus() != 0 {
		t.Fatalf("focus = %d, want 0", model.Focus())
	}
}

func TestAppModelHelpAndReconnect(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("?")})
	model = updated.(AppModel)
	if !model.ShowHelp() {
		t.Fatal("ShowHelp = false, want true")
	}

	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("r")})
	msg := cmd()
	if _, ok := msg.(ReconnectMsg); !ok {
		t.Fatalf("msg = %#v, want ReconnectMsg", msg)
	}
}

func TestAppModelViewShowsStatus(t *testing.T) {
	appStore := store.New()
	appStore.SetConnectionStatus(domain.ConnectionStatusConnected)
	model := NewAppModel(appStore, config.RuntimeConfig{})
	if !strings.Contains(model.View(), "状态: connected") {
		t.Fatalf("view = %q", model.View())
	}
	if !strings.Contains(model.View(), "h/l 或左右切换标签") || !strings.Contains(model.View(), "ctrl+t 主题") {
		t.Fatalf("view = %q", model.View())
	}
}

func TestAppModelThemeToggleChangesThemeName(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	initial := model.ThemeName()
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyCtrlT})
	model = updated.(AppModel)
	if model.ThemeName() == initial {
		t.Fatalf("theme did not change")
	}
}

func TestAppModelLocaleToggleChangesLabels(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	model.SetLocale(LocaleEnUS)
	view := model.View()
	if !strings.Contains(view, "Status: disconnected") {
		t.Fatalf("view = %q", view)
	}
}

func TestAppModelExpandedModeChangesLayout(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})
	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyCtrlF})
	model = updated.(AppModel)
	view := model.View()
	// 扩展模式不显示提示行（其中含 ctrl+t/ctrl+l/ctrl+f 等快捷键说明）
	if strings.Contains(view, "ctrl+t 主题") {
		t.Fatalf("expanded view should hide hint line, got %q", view)
	}
}

func TestAppModelFriendsViewShowsPendingRequests(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveNav(domain.NavKeyFriends)
	appStore.SetFriendRequests(
		[]domain.FriendRequestSummary{{UserID: "user-2", RequestMessage: "hello"}},
		[]domain.FriendRequestSummary{{UserID: "user-3", RequestMessage: "hi"}},
	)
	model := NewAppModel(appStore, config.RuntimeConfig{})
	view := model.View()
	if !strings.Contains(view, "/accept user-2") || !strings.Contains(view, "/cancel user-3") {
		t.Fatalf("view = %q, want incoming and outgoing friend requests", view)
	}
}

func TestAppModelInputSubmit(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("/")})
	model = updated.(AppModel)
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("h")})
	model = updated.(AppModel)
	updated, _ = model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("i")})
	model = updated.(AppModel)
	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	msg := cmd()

	submit, ok := msg.(SubmitInputMsg)
	if !ok {
		t.Fatalf("msg = %#v, want SubmitInputMsg", msg)
	}
	if submit.Text != "/hi" {
		t.Fatalf("text = %q, want /hi", submit.Text)
	}
}

func TestAppModelEnterMovesFromNavToList(t *testing.T) {
	model := NewAppModel(store.New(), config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyEnter})
	model = updated.(AppModel)
	if model.Focus() != 1 {
		t.Fatalf("focus = %d, want 1", model.Focus())
	}
}

func TestAppModelFriendsViewShowsSelection(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveNav(domain.NavKeyFriends)
	appStore.SetCurrentUserID("u100")
	appStore.SetFriends([]domain.FriendSummary{
		{UserID: "u100", DisplayName: "Alice"},
		{UserID: "u200", DisplayName: "Bob"},
	})
	model := NewAppModel(appStore, config.RuntimeConfig{})

	updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyTab})
	model = updated.(AppModel)
	view := model.View()

	if !strings.Contains(view, "> Alice") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "enter 打开会话") {
		t.Fatalf("view = %q", view)
	}
}

func TestAppModelConversationListShowsUnreadBadge(t *testing.T) {
	appStore := store.New()
	appStore.UpsertConversation(domain.ConversationSummary{
		ConversationID:  "s:u100:u200",
		Title:           "Alice",
		UnreadCount:     3,
		LastMessageTime: 1,
	})
	model := NewAppModel(appStore, config.RuntimeConfig{})
	view := model.View()
	if !strings.Contains(view, "Alice") || !strings.Contains(view, "3") {
		t.Fatalf("view = %q", view)
	}
}

func TestAppModelSelectedFriendConversationIDUsesCurrentUser(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveNav(domain.NavKeyFriends)
	appStore.SetCurrentUserID("u200")
	appStore.SetFriends([]domain.FriendSummary{{UserID: "u100", DisplayName: "Alice"}})
	model := NewAppModel(appStore, config.RuntimeConfig{})
	model.focus = focusList

	conversationID, ok := model.selectedConversationID()
	if !ok {
		t.Fatal("selectedConversationID() = false, want true")
	}
	if conversationID != "s:u100:u200" {
		t.Fatalf("conversationID = %q, want s:u100:u200", conversationID)
	}
}

func TestAppModelChatViewOnlyShowsRecentMessages(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveConversation("s:u100:u200")
	items := make([]domain.MessageItem, 0, 12)
	for i := 0; i < 12; i++ {
		senderID := "u100"
		self := false
		if i%2 == 1 {
			senderID = "u200"
			self = true
		}
		items = append(items, domain.MessageItem{
			ID:          "m",
			SenderID:    senderID,
			SenderLabel: senderID,
			Content:     "msg" + string(rune('A'+i)),
			Self:        self,
		})
	}
	appStore.SetMessages("s:u100:u200", items)
	model := NewAppModel(appStore, config.RuntimeConfig{})

	// innerW=56 复现旧布局，高度需容纳 5 组(14行)+标题(2行)+输入区(2行)=18 行
	view := model.chatView(defaultTheme(), 56, 20)
	if strings.Contains(view, "msgA") || strings.Contains(view, "msgB") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "... 7 个更早的消息组") || !strings.Contains(view, "msgL") {
		t.Fatalf("view = %q", view)
	}
	if !strings.Contains(view, "> ") {
		t.Fatalf("view = %q", view)
	}
}

func TestBuildChatGroupsMergesContinuousMessages(t *testing.T) {
	groups := buildChatGroups([]domain.MessageItem{
		{SenderID: "u200", SenderLabel: "alice", Content: "one"},
		{SenderID: "u200", SenderLabel: "alice", Content: "two"},
		{SenderID: "u100", SenderLabel: "me", Content: "three", Self: true},
	})
	if len(groups) != 2 {
		t.Fatalf("len(groups) = %d, want 2", len(groups))
	}
	if groups[0].label != "alice" || len(groups[0].items) != 2 {
		t.Fatalf("group[0] = %#v", groups[0])
	}
	if groups[1].label != "me" || !groups[1].self || len(groups[1].items) != 1 {
		t.Fatalf("group[1] = %#v", groups[1])
	}
}

func TestRenderChatGroupUsesLeftRightDialogueLayout(t *testing.T) {
	other := renderChatGroup(chatMessageGroup{
		label: "alice",
		self:  false,
		items: []string{"hello", "again"},
	}, defaultTheme(), 56, 34)
	self := renderChatGroup(chatMessageGroup{
		label: "me",
		self:  true,
		items: []string{"hi"},
	}, defaultTheme(), 56, 34)

	otherLines := strings.Split(other, "\n")
	selfLines := strings.Split(self, "\n")
	if len(otherLines) < 3 || len(selfLines) < 2 {
		t.Fatalf("rendered blocks too short: other=%q self=%q", other, self)
	}
	if !strings.HasPrefix(strings.TrimSpace(otherLines[0]), "alice") {
		t.Fatalf("other meta = %q", otherLines[0])
	}
	if !strings.HasPrefix(strings.TrimSpace(selfLines[0]), "me") {
		t.Fatalf("self meta = %q", selfLines[0])
	}
	if !strings.HasPrefix(strings.TrimSpace(otherLines[1]), "hello") {
		t.Fatalf("other bubble should be left aligned, got %q", otherLines[1])
	}
	if !strings.HasPrefix(strings.TrimSpace(otherLines[2]), "again") {
		t.Fatalf("group continuation should not repeat header, got %q", otherLines[2])
	}
	if !strings.HasPrefix(selfLines[1], " ") {
		t.Fatalf("self bubble should be right aligned, got %q", selfLines[1])
	}
	if !strings.HasSuffix(strings.TrimSpace(selfLines[1]), "hi") {
		t.Fatalf("self bubble should be right aligned, got %q", selfLines[1])
	}
}

func TestChatViewAddsSpacingBetweenMessageGroups(t *testing.T) {
	appStore := store.New()
	appStore.SetActiveConversation("s:u100:u200")
	appStore.SetMessages("s:u100:u200", []domain.MessageItem{
		{SenderID: "u200", SenderLabel: "alice", Content: "one"},
		{SenderID: "u100", SenderLabel: "me", Content: "two", Self: true},
	})
	model := NewAppModel(appStore, config.RuntimeConfig{})
	// innerW=56 保持对齐宽度不变，分隔线取代空行+label，测试组间距依旧成立
	view := model.chatView(defaultTheme(), 56, panelBodyHeight)
	if !strings.Contains(view, "\n\n                                                      me") {
		t.Fatalf("view = %q", view)
	}
}
