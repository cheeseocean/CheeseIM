import type {
  GatewayClient,
  GatewayEvent,
  SendMessageRequest,
  SendTextRequest,
  SendTextResult,
} from './contracts';
import type { AuthSession, GatewayConnection, MessageItem, WsTicket } from '../domain/types';
import {
  buildAuthRequest,
  buildConnectRequest,
  buildSendMessageRequest,
  commandTypes,
  type ServerEnvelope,
} from '../transport/envelope';

interface RealGatewayClientOptions {
  socketFactory?: (url: string) => WebSocket;
}

interface PendingSend {
  resolve(value: SendTextResult): void;
  reject(error: Error): void;
}

interface PendingConnect {
  resolve(connection: GatewayConnection): void;
  reject(error: Error): void;
}

const FRIEND_STATE_EVENT_TYPES = new Set([
  'friend_request_created',
  'friend_request_accepted',
  'friend_request_rejected',
  'friend_request_cancelled',
]);

export function createRealGatewayClient(
  options: RealGatewayClientOptions = {},
): GatewayClient {
  const socketFactory = options.socketFactory ?? ((url: string) => new WebSocket(url));
  let socket: WebSocket | null = null;
  let activeSession: AuthSession | null = null;
  let currentTicket: WsTicket | null = null;
  let operationCounter = 0;
  let pendingConnectRequestId: string | null = null;
  let pendingConnectConnId: string | null = null;
  let pendingAuthRequestId: string | null = null;
  let pendingConnect: PendingConnect | null = null;
  const pendingSends = new Map<string, PendingSend>();
  const listeners = new Set<(event: GatewayEvent) => void>();

  function nextOperationId(prefix: string): string {
    operationCounter += 1;
    return `${prefix}-${String(operationCounter).padStart(8, '0')}`;
  }

  function emit(event: GatewayEvent): void {
    listeners.forEach((listener) => listener(event));
  }

  function rejectPendingSends(message: string): void {
    pendingSends.forEach((pending) => {
      pending.reject(new Error(message));
    });
    pendingSends.clear();
  }

  function handleAuthSuccess(message: ServerEnvelope<Record<string, unknown> | string>): void {
    if (
      pendingConnect == null ||
      pendingAuthRequestId == null ||
      message.requestId !== pendingAuthRequestId
    ) {
      return;
    }
    if (!isAuthSuccessBody(message.body)) {
      pendingConnect.reject(new Error(stringMessage(message.body, 'ticket rejected')));
      pendingConnect = null;
      pendingAuthRequestId = null;
      return;
    }

    const connection = {
      connId: String(message.body.connId ?? pendingConnectConnId ?? nextOperationId('conn')),
      lifecycle: 'connected',
      transportLabel: 'PostOffice WebSocket',
    } satisfies GatewayConnection;

    pendingConnectConnId = null;
    pendingAuthRequestId = null;
    pendingConnectRequestId = null;
    pendingConnect.resolve(connection);
    pendingConnect = null;
  }

  function handleAuthError(message: ServerEnvelope<Record<string, unknown> | string>): void {
    if (pendingConnect == null || message.requestId !== pendingAuthRequestId) {
      return;
    }
    const reason = stringMessage(message.body, 'ticket rejected');
    pendingConnect.reject(new Error(reason));
    pendingConnect = null;
    pendingConnectConnId = null;
    pendingConnectRequestId = null;
    pendingAuthRequestId = null;
  }

  function handleChatSend(message: ServerEnvelope<Record<string, unknown> | string>): void {
    const pending = pendingSends.get(message.requestId);
    if (pending == null) {
      return;
    }

    const payload = typeof message.body === 'object' && message.body != null ? (message.body as Record<string, unknown>) : {};
    pending.resolve({
      serverId: String(payload.serverMsgID ?? payload.serverId ?? ''),
      sentAt: Number(payload.sendTime ?? Date.now()),
    });
    pendingSends.delete(message.requestId);
  }

  function handleChatRecv(message: ServerEnvelope<Record<string, unknown> | string>): void {
    if (activeSession == null || typeof message.body !== 'object' || message.body == null) {
      return;
    }

    const payload = message.body as Record<string, unknown>;
    if (isFriendStateChangePayload(payload)) {
      emit({ type: 'friendStateChanged' });
      return;
    }

    emit(mapGatewayEvent(payload, activeSession));
  }

  function handleForceLogout(message: ServerEnvelope<Record<string, unknown> | string>): void {
    const reason = stringMessage(message.body, 'Force logout');
    rejectPendingSends(reason);
    if (pendingConnect != null) {
      pendingConnect.reject(new Error(reason));
      pendingConnect = null;
      pendingAuthRequestId = null;
    }
    activeSession = null;
    emit({
      type: 'forceLogout',
      reason,
    });
  }

  function handleError(message: ServerEnvelope<Record<string, unknown> | string>): void {
    if (pendingConnect != null && message.requestId === pendingConnectRequestId) {
      const reason = stringMessage(message.body, 'connect rejected');
      pendingConnect.reject(new Error(reason));
      pendingConnect = null;
      pendingConnectRequestId = null;
      pendingAuthRequestId = null;
      activeSession = null;
      currentTicket = null;
      return;
    }

    const pending = pendingSends.get(message.requestId);
    if (pending != null) {
      pending.reject(new Error(stringMessage(message.body, 'gateway error')));
      pendingSends.delete(message.requestId);
      return;
    }

    if (message.requestId === pendingAuthRequestId && pendingConnect != null) {
      handleAuthError(message);
    }
  }

  function handleMessage(message: ServerEnvelope<Record<string, unknown> | string>): void {
    switch (message.command) {
      case commandTypes.connect:
        if (
          pendingConnect == null ||
          pendingConnectRequestId == null ||
          message.requestId !== pendingConnectRequestId ||
          pendingAuthRequestId != null
        ) {
          return;
        }
        pendingConnectConnId = extractConnId(message.body);
        pendingAuthRequestId = nextOperationId('op-auth');
        socket?.send(
          JSON.stringify(
            buildAuthRequest(pendingAuthRequestId, {
              ticket: currentTicket?.ticket ?? '',
            }),
          ),
        );
        return;
      case commandTypes.auth:
        handleAuthSuccess(message);
        return;
      case commandTypes.chatSend:
        handleChatSend(message);
        return;
      case commandTypes.chatRecv:
        handleChatRecv(message);
        return;
      case commandTypes.forceLogout:
        handleForceLogout(message);
        return;
      case commandTypes.error:
        handleError(message);
        return;
    }
  }

  function sendMessageInternal(input: SendMessageRequest): Promise<SendTextResult> {
    if (socket == null || socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected.');
    }

    const requestId = nextOperationId('op-send');
    const payload = buildSendMessageRequest(requestId, {
      clientMsgID: input.localId,
      recvID: input.recipientId,
      content: input.content,
      contentType: input.contentType,
      sessionType: 1,
      attachedInfo: input.attachedInfo,
    });

    return new Promise((resolve, reject) => {
      pendingSends.set(requestId, { resolve, reject });
      socket?.send(JSON.stringify(payload));
    });
  }

  return {
    connect(ticket: WsTicket, session: AuthSession): Promise<GatewayConnection> {
      currentTicket = ticket;
      activeSession = session;
      pendingConnectRequestId = nextOperationId('op-connect');
      pendingConnectConnId = null;
      pendingAuthRequestId = null;

      return new Promise((resolve, reject) => {
        pendingConnect = { resolve, reject };
        socket = socketFactory(ticket.wsUrl);

        socket.addEventListener('open', () => {
          socket?.send(JSON.stringify(buildConnectRequest(pendingConnectRequestId!)));
        });

        socket.addEventListener('message', (event) => {
          let message: ServerEnvelope<Record<string, unknown> | string>;
          try {
            message = JSON.parse(String(event.data)) as ServerEnvelope<Record<string, unknown> | string>;
          } catch {
            return;
          }
          handleMessage(message);
        });

        socket.addEventListener('close', () => {
          rejectPendingSends('WebSocket connection closed.');
          if (pendingConnect != null) {
            pendingConnect.reject(new Error('WebSocket connection closed.'));
            pendingConnect = null;
          }
          pendingConnectConnId = null;
          pendingConnectRequestId = null;
          pendingAuthRequestId = null;
          activeSession = null;
          currentTicket = null;
          emit({ type: 'disconnected' });
        });

        socket.addEventListener('error', () => {
          if (pendingConnect != null) {
            pendingConnect.reject(new Error('WebSocket connection failed.'));
            pendingConnect = null;
            pendingAuthRequestId = null;
          }
          pendingConnectConnId = null;
          pendingConnectRequestId = null;
        });
      });
    },
    sendText(input: SendTextRequest): Promise<SendTextResult> {
      return sendMessageInternal({
        conversationId: input.conversationId,
        recipientId: input.recipientId,
        localId: input.localId,
        content: input.text,
        contentType: 101,
        session: input.session,
      });
    },
    sendMessage(input: SendMessageRequest): Promise<SendTextResult> {
      return sendMessageInternal(input);
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

function isAuthSuccessBody(body: unknown): body is Record<string, unknown> {
  return (
    body != null &&
    typeof body === 'object' &&
    ('connId' in body || 'userID' in body || 'userId' in body)
  );
}

function extractConnId(body: unknown): string | null {
  if (body != null && typeof body === 'object' && 'connId' in body) {
    const connId = (body as { connId?: unknown }).connId;
    if (connId != null && String(connId).trim() !== '') {
      return String(connId);
    }
  }
  return null;
}

function isFriendStateChangePayload(payload: Record<string, unknown>): boolean {
  const ext =
    payload.ext != null && typeof payload.ext === 'object'
      ? (payload.ext as Record<string, unknown>)
      : undefined;
  const notificationType = ext?.notificationType;
  return typeof notificationType === 'string' && FRIEND_STATE_EVENT_TYPES.has(notificationType);
}

function mapGatewayEvent(payload: Record<string, unknown>, session: AuthSession): GatewayEvent {
  const ext = payload.ext as Record<string, unknown> | undefined;
  const senderId = String(payload.sendID ?? payload.sendId ?? payload.senderId ?? ext?.senderId ?? '');
  const recipientId = String(payload.recvID ?? payload.recvId ?? payload.receiverId ?? ext?.recvId ?? '');
  const conversationId = String(
    payload.conversationId ??
      payload.conversationID ??
      payload.recvID ??
      payload.recvId ??
      ext?.recvId ??
      senderId,
  );
  const contentType = Number(payload.contentType ?? 101);

  if (contentType === 4002) {
    return {
      type: 'typing',
      conversationId,
      senderId,
    };
  }

  if (contentType === 2004) {
    return {
      type: 'read',
      conversationId,
      seq: Number(payload.content ?? payload.seq ?? 0),
      senderId,
      recipientId,
      clientMsgId: String(
        payload.clientMsgID ??
          payload.clientMsgId ??
          payload.serverMsgID ??
          payload.serverMsgId ??
          '',
      ),
    };
  }

  if (contentType === 2005) {
    return {
      type: 'revoke',
      conversationId,
      serverId: String(payload.content ?? payload.serverMsgID ?? payload.serverMsgId ?? ''),
    };
  }

  return {
    type: 'messageReceived',
    message: mapInboundMessage(payload, session),
  };
}

function stringMessage(value: unknown, fallback: string): string {
  if (typeof value === 'string' && value.trim() !== '') {
    return value;
  }
  if (value != null && typeof value === 'object') {
    if ('message' in value && (value as { message?: unknown }).message != null) {
      return String((value as { message?: unknown }).message);
    }
    if ('reason' in value && (value as { reason?: unknown }).reason != null) {
      return String((value as { reason?: unknown }).reason);
    }
  }
  return fallback;
}

function mapInboundMessage(
  payload: Record<string, unknown>,
  session: AuthSession,
): MessageItem {
  const ext = payload.ext as Record<string, unknown> | undefined;
  const senderId = String(payload.sendID ?? payload.sendId ?? payload.senderId ?? ext?.senderId ?? '');
  const conversationId = String(
    payload.conversationId ??
      payload.conversationID ??
      payload.recvID ??
      payload.recvId ??
      ext?.recvId ??
      senderId,
  );
  return {
    localId: String(
      payload.clientMsgID ??
        payload.clientMsgId ??
        payload.serverMsgID ??
        payload.serverMsgId ??
        `msg_${Date.now()}`,
    ),
    serverId:
      payload.serverMsgID == null && payload.serverMsgId == null
        ? undefined
        : String(payload.serverMsgID ?? payload.serverMsgId),
    seq: payload.seq == null ? undefined : Number(payload.seq),
    conversationId,
    senderId,
    senderDisplay: senderId === session.profile.userId ? session.profile.displayName : senderId,
    direction: senderId === session.profile.userId ? 'outgoing' : 'incoming',
    text: String(payload.content ?? ''),
    timestamp: Number(payload.sendTime ?? Date.now()),
    status: senderId === session.profile.userId ? 'delivered' : 'received',
  };
}
