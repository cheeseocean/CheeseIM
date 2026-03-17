# Message Structures

The repository currently keeps two message representations on purpose:

- `common.entity.Message`
- `common.dto.MessageProto`

## `Message`

`Message` is the legacy transport-shaped envelope still used by a few boundary adapters and provider-facing paths.

It retains OpenIM-style field names such as:

- `clientMsgID`
- `serverMsgID`
- `sendID`
- `recvID`
- `groupID`
- `platformID`

Keep this type only where adapter compatibility still requires it.

## `MessageProto`

`MessageProto` is the cleaned delivery-path DTO used by the rebuilt IM architecture.

Key fields:

- `clientMsgId`
- `serverMsgId`
- `conversationId`
- `senderId`
- `receiverId`
- `content`
- `contentType`
- `sessionType`
- `sendTime`
- `sequence`
- `offlinePushInfo`

## Convergence Rule

New delivery-core logic should prefer `MessageProto`.

Use `Message` only at edges where an older transport contract or provider-facing API still makes replacement more expensive than the adapter.
