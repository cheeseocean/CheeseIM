# CheeseBox UI Theme, Tab Navigation, Locale, and Expanded Mode Design

## Goal

Refine CheeseBox from a functional TUI into a cleaner terminal chat application without coupling UI concerns into the SDK. The SDK remains responsible for IM protocol, sync, and realtime behavior. CheeseBox remains responsible for presentation, interaction, and local UI preferences.

This design covers four UI-facing capabilities:
- built-in theme switching
- tab-style navigation
- Chinese/English UI copy switching
- expanded layout mode for a more immersive chat view

## Scope

In scope:
- CheeseBox UI layer only
- runtime theme switching
- runtime locale switching
- tab-based top navigation
- expanded mode layout toggle
- help and hint text updates

Out of scope:
- SDK changes
- protocol changes
- persistence of theme/locale/mode across restarts
- OS-level terminal fullscreen control
- external theme files or user-defined themes
- backend-driven i18n

## Design Principles

- Keep CheeseBox as a thin TUI app over the Go SDK.
- Avoid introducing a heavy i18n or theming framework.
- Prefer explicit UI tokens and string tables over global implicit state.
- Treat expanded mode as a layout mode, not OS fullscreen.
- Preserve keyboard-first interaction.

## Theme System

### Built-in themes

CheeseBox will ship with three built-in themes:

1. `classic`
- default theme
- dark terminal-oriented palette
- low saturation, stable contrast
- best default for day-to-day development use

2. `matrix`
- high-contrast green-on-dark palette
- stronger focus and status emphasis
- suitable for demo or novelty use

3. `paper`
- light terminal-style palette
- softer borders and body text
- suitable for longer reading sessions

### Theme model

Add a UI theme model that only exposes styling tokens. It does not own business state.

Suggested token groups:
- title
- status
- panel border
- focus
- hint
- chat meta
- chat content self
- chat content other
- empty state
- tab active
- tab inactive
- tab focus

Implementation approach:
- keep all tokens in `internal/ui/theme.go`
- introduce a `ThemeName` enum-like type and a `Theme` struct
- expose `themeByName(name)` and `nextTheme(name)` helpers
- avoid direct hardcoded colors in view functions

### Runtime behavior

- default theme: `classic`
- shortcut: `t`
- `t` cycles through `classic -> matrix -> paper -> classic`
- current theme is held in UI state only

## Navigation Redesign

### Replace left nav with top tabs

The current left-side navigation column will be replaced by a top tab bar.

Tabs:
- `Chats`
- `Friends`
- `Groups`
- `Settings`

### Layout

Normal layout becomes:
- title line
- status line
- tab bar
- content region
- hint line

Content region:
- `Chats`, `Friends`, `Groups`: list + chat/detail pane
- `Settings`: single-pane settings content

This frees horizontal space for message rendering and makes the app look more intentional.

### Focus model

Replace `focusNav` with `focusTabs`.

Focus areas become:
- tabs
- list
- input

Keyboard behavior:
- `Tab`: move focus forward
- `Esc`: return focus to tabs
- `h/l` or left/right arrows: switch active tab when tab area is focused
- `j/k` or up/down arrows: move within list when list area is focused
- `Enter`: open selected conversation
- `c/f/g/s`: keep as quick direct tab shortcuts

## Locale Switching

### Languages

Support two built-in UI locales:
- `zh-CN`
- `en-US`

### Scope of translation

Translate only local UI copy owned by CheeseBox:
- app title
- login labels and hints
- status labels
- tab labels
- help text
- empty states
- settings labels
- fixed toasts emitted by CheeseBox

Do not translate:
- backend error strings
- user-generated content
- names returned by SDK/server

### Implementation

Add a small UI locale layer, for example `internal/ui/locale.go`.

Design:
- define a `LocaleName` type
- define stable keys for UI strings
- implement `T(locale, key)` lookup
- keep string tables in code for now

Default locale:
- `zh-CN`

Runtime behavior:
- shortcut: `l`
- toggles `zh-CN <-> en-US`

This is intentionally lightweight and avoids introducing a general-purpose i18n dependency.

## Expanded Mode

### Meaning

Expanded mode is an internal CheeseBox layout mode. It is not OS fullscreen.

Purpose:
- reduce chrome
- enlarge the useful content area for reading and chatting
- support a more focused messaging experience in the terminal

### Behavior

Normal mode:
- all panel borders visible
- title, status, tabs, hints all visible
- list and chat use standard widths

Expanded mode:
- preserve a minimal top bar
- reduce or simplify borders
- compress hints
- allocate more width and height to the chat pane
- settings/help also render with wider content regions

Shortcut:
- `F11` if terminal forwards it cleanly
- fallback: `ctrl+f`

Implementation note:
- the UI should support both, but `ctrl+f` should be treated as the reliable binding

## Chat View Impact

The current grouped developer-style chat layout remains the base:
- left/right alignment stays
- sender header only appears when sender group changes
- repeated consecutive messages do not repeat headers

Theme tokens will style:
- sender header emphasis
- self vs other content color
- empty state text

Expanded mode will mainly affect available width and panel chrome, not the grouping model itself.

## State Changes

Add UI-only state to the root/app layer:
- active theme name
- active locale name
- expanded mode enabled flag

These values should live in CheeseBox UI state, not in SDK state.

## Testing Strategy

Add or update UI tests for:
- theme cycling changes rendered style-dependent output in a stable way
- tab navigation replaces left nav and still supports focus movement
- locale switch changes visible labels and help text
- expanded mode alters layout chrome without breaking content rendering
- grouped chat rendering still works under each layout mode

Focus tests on:
- visible labels
- focus behavior
- presence/absence of sections
- stable rendering invariants

Avoid brittle full-string snapshots of the entire terminal view.

## Migration Plan

1. Introduce theme and locale state plus token tables.
2. Refactor existing style globals to theme-driven style builders.
3. Replace left nav rendering with tab bar rendering.
4. Update focus behavior and help text.
5. Add locale-aware labels in login, app, and help views.
6. Add expanded mode layout switch.
7. Update tests.

## Risks

- Layout churn can break current width assumptions in chat rendering.
- Full-string UI tests can become fragile if overused.
- Expanded mode can accidentally hide essential affordances if over-minimized.

Mitigations:
- keep rendering helpers focused and composable
- prefer targeted assertions over full snapshots
- preserve quick shortcuts even when layout changes
