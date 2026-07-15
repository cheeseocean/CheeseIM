# CheeseBox

CheeseBox is the Bubble Tea TUI integration client for CheeseIM.

It now consumes the reusable Go IM SDK in `sdks/go` for:
- login and ws-ticket auth
- TCP realtime messaging over the server-owned Protobuf protocol
- seq-based history sync
- reconnect sync and gap repair
- friend/group/conversation queries

CheeseBox itself only keeps app-specific state and UI rendering. Its default all-in-one endpoint is `http://127.0.0.1:18079` with TCP at `127.0.0.1:5148`; each can be overridden through `CHEESEBOX_API_BASE_URL` and `CHEESEBOX_TCP_ADDR`.
