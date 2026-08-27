# CheeseBox

CheeseBox is the Bubble Tea TUI integration client for CheeseIM.

It now consumes the reusable Go IM SDK in `sdks/go` for:
- login and ws-ticket auth
- TCP realtime messaging over the server-owned Protobuf protocol
- broker acceptance, device delivery, and peer read high-watermark status
- seq-based history sync
- reconnect sync and gap repair
- friend/group/conversation queries

CheeseBox itself only keeps app-specific state and UI rendering. Its default all-in-one endpoint is `http://127.0.0.1:18079` with TCP at `127.0.0.1:5148`; each can be overridden through `CHEESEBOX_API_BASE_URL` and `CHEESEBOX_TCP_ADDR`.

The second login field is a short-lived, one-time identity assertion issued by the trusted account domain; it is not a password. The SDK forwards it as `identityAssertion` to `/api/auth/login`. For local integration, use the development issuer documented in `docs/client-runbook.md`.

Outgoing direct-chat text messages expose `sending → broker_accepted → delivered → read` from server-confirmed high-watermark events.
Use `/revoke last [reason]` for the latest outgoing message, or `/revoke <serverMsgId> [reason]` for an explicit target. Revoke notifications replace matching content with a tombstone even when the notification arrives before the message.

Typing is emitted automatically while editing a normal message. START is client-throttled and server-clamped to a short TTL; submit, clear, or leaving the input sends best-effort STOP. Remote indicators always self-expire from the server timestamp.
