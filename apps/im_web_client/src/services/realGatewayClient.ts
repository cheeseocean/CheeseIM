import type { GatewayClient, GatewayEvent, SendTextRequest, SendTextResult } from './contracts';
import type { AuthSession, GatewayConnection, MessageItem, WsTicket } from '../domain/types';

const WS_MESSAGE_TYPES = {
  connectReq: 1001,
  connectSuccess: 1002,
  authReq: 1101,
  authSuccess: 1102,
  authFailed: 1103,
  sendReq: 2001,
  sendResp: 2002,
  recvNotify: 2003,
  forceLogout: 7002,
  errorResp: 9001,
  paramError: 9002,
  permissionError: 9003,
  internalError: 9004,
} as const;

interface RealGatewayClientOptions {
  socketFactory?: (url: string) => WebSocket;
}

interface PendingSend {
  resolve(value: SendTextResult): void;
  reject(error: Error): void;
}

interface WsEnvelope<T = unknown> {
  msgType: number;
  operationID: string;
  data: T;
}

export function createRealGatewayClient(
  options: RealGatewayClientOptions = {},
): GatewayClient {
  const socketFactory = options.socketFactory ?? ((url: string) => new WebSocket(url));
  let socket: WebSocket | null = null;
  let activeSession: AuthSession | null = null;
  let operationCounter = 0;
  const pendingSends = new Map<string, PendingSend>();
  const listeners = new Set<(event: GatewayEvent) => void>();

  function nextOperationId(prefix: string): string {
    operationCounter += 1;
    return `${prefix}-${String(operationCounter).padStart(8, '0')}`;
  }

  function emit(event: GatewayEvent): void {
    listeners.forEach((listener) => listener(event));
  }

  return {
    connect(ticket: WsTicket, _session: AuthSession): Promise<GatewayConnection> {
      return new Promise((resolve, reject) => {
        activeSession = _session;
        socket = socketFactory(ticket.wsUrl);

        socket.addEventListener('open', () => {
          socket?.send(JSON.stringify(buildConnectRequest('system')));
        });

        socket.addEventListener('message', (event) => {
          const message = JSON.parse(String(event.data)) as WsEnvelope<Record<string, unknown> | string>;

          if (message.msgType === WS_MESSAGE_TYPES.connectSuccess) {
            socket?.send(JSON.stringify(buildWsTicketAuthRequest(nextOperationId('op-auth'), ticket.ticket)));
            return;
          }

          if (message.msgType === WS_MESSAGE_TYPES.authSuccess) {
            resolve({
              connId: String((message.data as Record<string, unknown>)?.connId ?? nextOperationId('conn')),
              lifecycle: 'connected',
              transportLabel: 'PostOffice WebSocket',
            });
            return;
          }

          if (message.msgType === WS_MESSAGE_TYPES.authFailed) {
            reject(new Error(stringMessage(message.data, 'ticket rejected')));
            return;
          }

          if (message.msgType === WS_MESSAGE_TYPES.sendResp) {
            const payload = message.data as Record<string, unknown>;
            pendingSends.get(message.operationID)?.resolve({
              serverId: String(payload.serverMsgID ?? payload.serverId ?? ''),
              sentAt: Number(payload.sendTime ?? Date.now()),
            });
            pendingSends.delete(message.operationID);
            return;
          }

          if (message.msgType === WS_MESSAGE_TYPES.recvNotify && activeSession != null) {
            emit({
              type: 'messageReceived',
              message: mapInboundMessage(message.data as Record<string, unknown>, activeSession),
            });
            return;
          }

          if (message.msgType === WS_MESSAGE_TYPES.forceLogout) {
            emit({
              type: 'forceLogout',
              reason: stringMessage(message.data, 'Force logout'),
            });
            return;
          }

          if (
            message.msgType === WS_MESSAGE_TYPES.errorResp ||
            message.msgType === WS_MESSAGE_TYPES.paramError ||
            message.msgType === WS_MESSAGE_TYPES.permissionError ||
            message.msgType === WS_MESSAGE_TYPES.internalError
          ) {
            pendingSends.get(message.operationID)?.reject(new Error(stringMessage(message.data, 'gateway error')));
            pendingSends.delete(message.operationID);
          }
        });

        socket.addEventListener('close', () => {
          pendingSends.forEach((pending) => {
            pending.reject(new Error('WebSocket connection closed.'));
          });
          pendingSends.clear();
          emit({ type: 'disconnected' });
        });

        socket.addEventListener('error', () => {
          reject(new Error('WebSocket connection failed.'));
        });
      });
    },
    sendText(input: SendTextRequest): Promise<SendTextResult> {
      if (socket == null || socket.readyState !== WebSocket.OPEN) {
        throw new Error('WebSocket is not connected.');
      }

      const operationID = nextOperationId('op-send');
      const payload = buildSendMessageRequest(operationID, {
        clientMsgID: input.localId,
        recvID: input.recipientId,
        content: input.text,
        contentType: 101,
        sessionType: 1,
      });

      return new Promise((resolve, reject) => {
        pendingSends.set(operationID, { resolve, reject });
        socket?.send(JSON.stringify(payload));
      });
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

export function buildConnectRequest(operationID: string): WsEnvelope<Record<string, never>> {
  return {
    msgType: WS_MESSAGE_TYPES.connectReq,
    operationID,
    data: {},
  };
}

export function buildWsTicketAuthRequest(
  operationID: string,
  ticket: string,
): WsEnvelope<{ ticket: string }> {
  return {
    msgType: WS_MESSAGE_TYPES.authReq,
    operationID,
    data: {
      ticket,
    },
  };
}

function buildSendMessageRequest(
  operationID: string,
  payload: {
    clientMsgID: string;
    recvID: string;
    content: string;
    contentType: number;
    sessionType: number;
  },
): WsEnvelope<typeof payload> {
  return {
    msgType: WS_MESSAGE_TYPES.sendReq,
    operationID,
    data: payload,
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
  const senderId = String(payload.sendID ?? payload.senderId ?? '');
  const conversationId = String(payload.recvID ?? payload.recvId ?? senderId);
  return {
    localId: String(payload.clientMsgID ?? payload.serverMsgID ?? `msg_${Date.now()}`),
    serverId: payload.serverMsgID == null ? undefined : String(payload.serverMsgID),
    conversationId,
    senderId,
    senderDisplay: senderId === session.profile.userId ? session.profile.displayName : senderId,
    direction: senderId === session.profile.userId ? 'outgoing' : 'incoming',
    text: String(payload.content ?? ''),
    timestamp: Number(payload.sendTime ?? Date.now()),
    status: senderId === session.profile.userId ? 'delivered' : 'received',
  };
}
