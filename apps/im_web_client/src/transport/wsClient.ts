import type { AuthSession } from '../domain/types';
import {
  buildAuthRequest,
  buildConnectRequest,
  buildSendMessageRequest,
  type SendMessagePayload,
  type WSMessage,
  wsMessageTypes,
} from './wsMessage';
import { classifySendMessageError } from './sendMessageError';

interface WsClientCallbacks {
  onConnecting?(): void;
  onReady?(): void;
  onAuthRejected?(message: string): void;
  onForceLogout?(message: string): void;
  onSocketClosed?(): void;
  onInboundMessage?(payload: Record<string, unknown>): void;
  onSendAck?(payload: Record<string, unknown>): void;
}

interface WsClientOptions {
  heartbeatIntervalMs?: number;
  reconnectDelaysMs?: number[];
}

export interface WsClient {
  connect(session: AuthSession): void;
  sendMessage(operationID: string, payload: SendMessagePayload): Promise<void>;
  disconnect(): void;
}

interface PendingRequest {
  resolve(): void;
  reject(error: Error): void;
}

export function createWsClient(
  socketFactory: (url: string) => WebSocket,
  callbacks: WsClientCallbacks = {},
  options: WsClientOptions = {},
): WsClient {
  let socket: WebSocket | null = null;
  let activeSession: AuthSession | null = null;
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let reconnectAttempt = 0;
  let operationCounter = 0;
  const pendingRequests = new Map<string, PendingRequest>();
  const heartbeatIntervalMs = options.heartbeatIntervalMs ?? 30_000;
  const reconnectDelaysMs = options.reconnectDelaysMs ?? [
    1_000,
    2_000,
    5_000,
    10_000,
    20_000,
  ];

  function nextOperationId(prefix: string): string {
    operationCounter += 1;
    return `${prefix}-${String(operationCounter).padStart(6, '0')}`;
  }

  function stopHeartbeat(): void {
    if (heartbeatTimer != null) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  function rejectPendingRequests(message: string): void {
    pendingRequests.forEach((request) => {
      request.reject(classifySendMessageError(message));
    });
    pendingRequests.clear();
  }

  function scheduleReconnect(): void {
    if (activeSession == null || reconnectTimer != null) {
      return;
    }
    const delay =
      reconnectDelaysMs[Math.min(reconnectAttempt, reconnectDelaysMs.length - 1)];
    reconnectAttempt += 1;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      connectInternal(activeSession!);
    }, delay);
  }

  function connectInternal(session: AuthSession): void {
    activeSession = session;
    socket = socketFactory(session.wsUrl);
    callbacks.onConnecting?.();
    socket.addEventListener('open', () => {
      socket?.send(JSON.stringify(buildConnectRequest(nextOperationId('op-connect'))));
    });
    socket.addEventListener('message', (event) => {
      const message = JSON.parse(String(event.data)) as WSMessage<
        Record<string, unknown> | string
      >;
      switch (message.msgType) {
        case wsMessageTypes.connectSuccess:
          socket?.send(
            JSON.stringify(
              buildAuthRequest(nextOperationId('op-auth'), {
                token: session.token,
                userID: session.userID,
                platformID: session.platformID,
              }),
            ),
          );
          return;
        case wsMessageTypes.authSuccess:
          reconnectAttempt = 0;
          stopHeartbeat();
          heartbeatTimer = setInterval(() => {
            socket?.send(
              JSON.stringify({
                msgType: wsMessageTypes.heartbeatReq,
                operationID: nextOperationId('op-heartbeat'),
                data: 'ping',
              }),
            );
          }, heartbeatIntervalMs);
          callbacks.onReady?.();
          return;
        case wsMessageTypes.authFailed:
          rejectPendingRequests(String(message.data));
          activeSession = null;
          stopHeartbeat();
          callbacks.onAuthRejected?.(String(message.data));
          return;
        case wsMessageTypes.forceLogoutNotify:
          rejectPendingRequests(
            typeof message.data === 'string'
              ? message.data
              : String((message.data as Record<string, unknown>).reason ?? 'Force logout'),
          );
          activeSession = null;
          stopHeartbeat();
          callbacks.onForceLogout?.(
            typeof message.data === 'string'
                ? message.data
                : String((message.data as Record<string, unknown>).reason ?? 'Force logout'),
          );
          return;
        case wsMessageTypes.sendMsgResp:
          pendingRequests.get(message.operationID)?.resolve();
          pendingRequests.delete(message.operationID);
          callbacks.onSendAck?.(message.data as Record<string, unknown>);
          return;
        case wsMessageTypes.recvMsgNotify:
          callbacks.onInboundMessage?.(message.data as Record<string, unknown>);
          return;
        case wsMessageTypes.errorResp:
        case wsMessageTypes.paramError:
        case wsMessageTypes.permissionError:
        case wsMessageTypes.internalError: {
          pendingRequests
            .get(message.operationID)
            ?.reject(classifySendMessageError(String(message.data), message.msgType));
          pendingRequests.delete(message.operationID);
          return;
        }
      }
    });
    socket.addEventListener('close', () => {
      stopHeartbeat();
      rejectPendingRequests('WebSocket connection closed.');
      callbacks.onSocketClosed?.();
      scheduleReconnect();
    });
  }

  return {
    connect(session) {
      if (reconnectTimer != null) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      connectInternal(session);
    },
    async sendMessage(operationID, payload) {
      if (socket == null) {
        throw new Error('WebSocket is not connected.');
      }
      const message: WSMessage<SendMessagePayload> = buildSendMessageRequest(
        operationID,
        payload,
      );
      const pending = new Promise<void>((resolve, reject) => {
        pendingRequests.set(operationID, { resolve, reject });
      });
      socket.send(JSON.stringify(message));
      return pending;
    },
    disconnect() {
      stopHeartbeat();
      if (reconnectTimer != null) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      activeSession = null;
      rejectPendingRequests('WebSocket disconnected.');
      socket?.close();
      socket = null;
    },
  };
}
