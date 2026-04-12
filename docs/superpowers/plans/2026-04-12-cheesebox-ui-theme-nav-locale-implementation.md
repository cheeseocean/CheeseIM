# CheeseBox UI Theme, Tabs, Locale, and Expanded Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add built-in theme switching, top tab navigation, Chinese/English UI copy switching, and expanded mode to CheeseBox while preserving its SDK-driven IM behavior.

**Architecture:** Keep all changes inside `apps/CheeseBox` and limit them to UI state, rendering, and local UI configuration. Introduce lightweight theme and locale tables, move navigation from a left rail to a top tab bar, and implement expanded mode as a layout variant rather than OS fullscreen.

**Tech Stack:** Go, Bubble Tea, Lipgloss, Bubbles textinput, Go test

---

## File Structure

### UI state and rendering
- Modify: `apps/CheeseBox/internal/ui/root_model.go`
  - Hold theme name, locale name, expanded mode state, and route new shortcut messages.
- Modify: `apps/CheeseBox/internal/ui/app_model.go`
  - Replace left navigation with tabs, apply theme-driven styles, use locale strings, and support expanded mode layout.
- Modify: `apps/CheeseBox/internal/ui/login_model.go`
  - Replace hardcoded copy with locale strings and apply theme-driven styling.
- Modify: `apps/CheeseBox/internal/ui/help_view.go`
  - Render locale-aware help copy and reflect new shortcuts.
- Modify: `apps/CheeseBox/internal/ui/messages.go`
  - Add explicit messages for theme, locale, and expanded mode toggles if needed.

### UI infrastructure
- Modify: `apps/CheeseBox/internal/ui/theme.go`
  - Replace global fixed styles with a theme model and helpers.
- Create: `apps/CheeseBox/internal/ui/locale.go`
  - Define locale names, string keys, and translation lookup.

### Tests
- Modify: `apps/CheeseBox/internal/ui/app_model_test.go`
  - Add tests for tab rendering, expanded mode rendering, and locale-aware labels.
- Modify: `apps/CheeseBox/internal/ui/root_model_test.go`
  - Add tests for toggle shortcuts and state persistence through disconnect/reconnect.
- Modify: `apps/CheeseBox/internal/config/config_test.go`
  - Only if runtime defaults need to expose locale or theme later. Otherwise leave untouched.

### Docs
- Modify: `apps/CheeseBox/README.md`
  - Document shortcuts for theme, locale, and expanded mode.
- Modify: `apps/CheeseBox/arch.md`
  - Update UI structure notes to reflect tabs and theme/locale state.

---

### Task 1: Introduce Theme Tokens

**Files:**
- Modify: `apps/CheeseBox/internal/ui/theme.go`
- Test: `apps/CheeseBox/internal/ui/app_model_test.go`

- [ ] **Step 1: Write the failing test**

Add a test that verifies theme cycling changes rendered output markers or style-dependent labels in a stable way.

```go
func TestAppModelThemeToggleChangesThemeName(t *testing.T) {
    model := NewAppModel(store.New(), config.RuntimeConfig{})
    initial := model.ThemeName()
    updated, _ := model.Update(tea.KeyMsg{Type: tea.KeyRunes, Runes: []rune("t")})
    model = updated.(AppModel)
    if model.ThemeName() == initial {
        t.Fatalf("theme did not change")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui -run TestAppModelThemeToggleChangesThemeName
```
Expected: FAIL because `ThemeName()` or `t` handling does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Implement:
- `ThemeName` type
- `Theme` struct with tokenized styles
- built-in themes: `classic`, `matrix`, `paper`
- helper functions: `themeByName`, `nextTheme`
- `AppModel` theme state and `t` toggle handling
- `ThemeName()` helper for testing if needed

- [ ] **Step 4: Run test to verify it passes**

Run the same test command.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/theme.go apps/CheeseBox/internal/ui/app_model.go apps/CheeseBox/internal/ui/app_model_test.go
git commit -m "feat: add CheeseBox built-in themes"
```

### Task 2: Add Locale Table and Runtime Toggle

**Files:**
- Create: `apps/CheeseBox/internal/ui/locale.go`
- Modify: `apps/CheeseBox/internal/ui/app_model.go`
- Modify: `apps/CheeseBox/internal/ui/login_model.go`
- Modify: `apps/CheeseBox/internal/ui/help_view.go`
- Modify: `apps/CheeseBox/internal/ui/root_model.go`
- Test: `apps/CheeseBox/internal/ui/app_model_test.go`
- Test: `apps/CheeseBox/internal/ui/root_model_test.go`

- [ ] **Step 1: Write the failing tests**

Add tests that verify:
- locale defaults to Chinese
- pressing `l` switches visible labels
- help text changes with locale

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui -run 'TestAppModelLocale|TestHelpViewLocale|TestRootModelLocale'
```
Expected: FAIL because locale state and translation lookup do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement:
- `LocaleName` type with `zh-CN` and `en-US`
- stable translation keys and `T(locale, key)` lookup
- locale state on the UI model
- `l` shortcut to toggle locale
- locale-aware copy in login view, app view, hints, help text, tabs, and empty states

- [ ] **Step 4: Run tests to verify they pass**

Run the same command.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/locale.go apps/CheeseBox/internal/ui/app_model.go apps/CheeseBox/internal/ui/login_model.go apps/CheeseBox/internal/ui/help_view.go apps/CheeseBox/internal/ui/root_model.go apps/CheeseBox/internal/ui/app_model_test.go apps/CheeseBox/internal/ui/root_model_test.go
git commit -m "feat: add CheeseBox locale switching"
```

### Task 3: Replace Left Nav with Top Tabs

**Files:**
- Modify: `apps/CheeseBox/internal/ui/app_model.go`
- Modify: `apps/CheeseBox/internal/ui/theme.go`
- Test: `apps/CheeseBox/internal/ui/app_model_test.go`

- [ ] **Step 1: Write the failing tests**

Add tests that verify:
- navigation renders as top tabs
- `h/l` or left/right switches tabs when tabs are focused
- `c/f/g/s` quick switches still work
- old left-nav heading no longer appears

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui -run 'TestAppModelTabs|TestAppModelNavigationSwitching|TestAppModelView'
```
Expected: FAIL because the current UI still uses the left nav panel.

- [ ] **Step 3: Write minimal implementation**

Implement:
- replace left nav pane with top tab bar rendering
- rename focus area from nav to tabs where appropriate
- update focus movement and hint text
- use theme-provided tab active/inactive/focus styles
- keep list and chat panes for Chats/Friends/Groups
- render Settings as a single-pane content area under the tab bar

- [ ] **Step 4: Run tests to verify they pass**

Run the same command.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/app_model.go apps/CheeseBox/internal/ui/theme.go apps/CheeseBox/internal/ui/app_model_test.go
 git commit -m "feat: replace CheeseBox side nav with tabs"
```

### Task 4: Add Expanded Mode

**Files:**
- Modify: `apps/CheeseBox/internal/ui/app_model.go`
- Modify: `apps/CheeseBox/internal/ui/root_model.go`
- Test: `apps/CheeseBox/internal/ui/app_model_test.go`
- Test: `apps/CheeseBox/internal/ui/root_model_test.go`

- [ ] **Step 1: Write the failing tests**

Add tests that verify:
- `ctrl+f` toggles expanded mode
- expanded mode reduces chrome or hides redundant sections
- chat pane gains more usable area than normal mode

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui -run 'TestAppModelExpandedMode|TestRootModelExpandedMode'
```
Expected: FAIL because expanded mode does not exist.

- [ ] **Step 3: Write minimal implementation**

Implement:
- expanded mode boolean in UI state
- reliable toggle via `ctrl+f`
- optional `F11` handling if Bubble Tea forwards it consistently in this harness
- alternate layout widths/heights/chrome for expanded mode
- keep essential top status visible

- [ ] **Step 4: Run tests to verify they pass**

Run the same command.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/app_model.go apps/CheeseBox/internal/ui/root_model.go apps/CheeseBox/internal/ui/app_model_test.go apps/CheeseBox/internal/ui/root_model_test.go
git commit -m "feat: add CheeseBox expanded mode"
```

### Task 5: Integrate Theme and Locale Across Login and Help Views

**Files:**
- Modify: `apps/CheeseBox/internal/ui/login_model.go`
- Modify: `apps/CheeseBox/internal/ui/help_view.go`
- Modify: `apps/CheeseBox/internal/ui/root_model.go`
- Test: `apps/CheeseBox/internal/ui/root_model_test.go`
- Test: `apps/CheeseBox/internal/ui/app_model_test.go`

- [ ] **Step 1: Write the failing tests**

Add targeted tests for:
- login view labels update with locale
- help view includes theme/locale/expanded shortcuts
- root view uses active theme and locale consistently while authenticated and disconnected

- [ ] **Step 2: Run tests to verify they fail**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./internal/ui -run 'TestRootModel|TestAppModel|TestLoginModel'
```
Expected: FAIL on missing localized/help-integrated output.

- [ ] **Step 3: Write minimal implementation**

Implement:
- route theme and locale into login/help rendering
- update help strings to include `t`, `l`, and expanded mode shortcut
- keep disconnect handling compatible with already-fixed authenticated state logic

- [ ] **Step 4: Run tests to verify they pass**

Run the same command.
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/CheeseBox/internal/ui/login_model.go apps/CheeseBox/internal/ui/help_view.go apps/CheeseBox/internal/ui/root_model.go apps/CheeseBox/internal/ui/root_model_test.go apps/CheeseBox/internal/ui/app_model_test.go
git commit -m "feat: localize CheeseBox login and help views"
```

### Task 6: Update Docs and Run Final Verification

**Files:**
- Modify: `apps/CheeseBox/README.md`
- Modify: `apps/CheeseBox/arch.md`

- [ ] **Step 1: Update docs**

Document:
- built-in themes
- theme shortcut `t`
- locale toggle `l`
- tab navigation keys
- expanded mode shortcut

- [ ] **Step 2: Run final verification**

Run:
```bash
cd apps/CheeseBox
GOCACHE=/tmp/cheesebox-gocache GOMODCACHE=/tmp/cheesebox-gomodcache /Users/xxxcrel/.gvm/gos/go1.25.0/bin/go test ./...
```
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add apps/CheeseBox/README.md apps/CheeseBox/arch.md
git commit -m "docs: document CheeseBox UI controls"
```
