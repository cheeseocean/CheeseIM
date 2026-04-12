# CheeseBox

CheeseBox is the Bubble Tea TUI client for CheeseIM.

It now consumes the reusable Go IM SDK in `sdks/go` for:
- login and ws-ticket auth
- TCP realtime messaging
- seq-based history sync
- reconnect sync and gap repair
- friend/group/conversation queries

CheeseBox itself only keeps app-specific state and UI rendering.
