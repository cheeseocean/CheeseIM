import type { AuthSession } from '../domain/types';
import {
  buildAuthRequest,
  buildConnectRequest,
  buildHeartbeatRequest,
  buildSendMessageRequest,
  commandTypes,
  type SendMessagePayload,
  type ServerEnvelope,
} from './envelope';
import { classifySendMessageError, SendMessageError } from './sendMessageError';

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
  sendMessage(requestId: string, payload: SendMessagePayload): Promise<void>;
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
  let pendingAuthRequestId: string | null = null;
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

  function startHeartbeat(): void {
    stopHeartbeat();
    heartbeatTimer = setInterval(() => {
      socket?.send(JSON.stringify(buildHeartbeatRequest(nextOperationId('op-heartbeat'))));
    }, heartbeatIntervalMs);
  }

  function rejectPendingRequests(message: string): void {
    pendingRequests.forEach((request) => {
      request.reject(new SendMessageError('connectionLost', message));
    });
    pendingRequests.clear();
  }

  function extractBodyMessage(body: unknown, fallback: string): string {
    if (typeof body === 'string' && body.trim() !== '') {
      return body;
    }
    if (body != null && typeof body === 'object') {
      const record = body as Record<string, unknown>;
      if (typeof record.message === 'string' && record.message.trim() !== '') {
        return record.message;
      }
      if (typeof record.reason === 'string' && record.reason.trim() !== '') {
        return record.reason;
      }
    }
    return fallback;
  }

  function isAuthSuccessBody(body: unknown): body is Record<string, unknown> {
    return (
      body != null &&
      typeof body === 'object' &&
      ('connId' in body || 'userID' in body || 'userId' in body)
    );
  }

  function handleAuthFailure(message: ServerEnvelope<unknown>): void {
    const reason = extractBodyMessage(message.body, 'Authentication rejected.');
    rejectPendingRequests(reason);
    pendingAuthRequestId = null;
    activeSession = null;
    stopHeartbeat();
    callbacks.onAuthRejected?.(reason);
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
    pendingAuthRequestId = null;
    socket = socketFactory(session.wsUrl);
    callbacks.onConnecting?.();
    socket.addEventListener('open', () => {
      socket?.send(JSON.stringify(buildConnectRequest(nextOperationId('op-connect'))));
    });
    socket.addEventListener('message', (event) => {
      let message: ServerEnvelope<Record<string, unknown> | string>;
      try {
        message = JSON.parse(String(event.data)) as ServerEnvelope<
          Record<string, unknown> | string
        >;
      } catch {
        return;
      }
      switch (message.command) {
        case commandTypes.connect:
          if (pendingAuthRequestId != null || heartbeatTimer != null || activeSession == null) {
            return;
          }
          pendingAuthRequestId = nextOperationId('op-auth');
          socket?.send(
            JSON.stringify(
              buildAuthRequest(pendingAuthRequestId, {
                token: session.token,
                userID: session.userID,
                platformID: session.platformID,
              }),
            ),
          );
          return;
        case commandTypes.auth:
          if (pendingAuthRequestId == null || message.requestId !== pendingAuthRequestId) {
            return;
          }
          if (!isAuthSuccessBody(message.body)) {
            handleAuthFailure(message);
            return;
          }
          pendingAuthRequestId = null;
          reconnectAttempt = 0;
          startHeartbeat();
          callbacks.onReady?.();
          return;
        case commandTypes.forceLogout: {
          const reason = extractBodyMessage(message.body, 'Force logout');
          rejectPendingRequests(reason);
          activeSession = null;
          stopHeartbeat();
          pendingAuthRequestId = null;
          callbacks.onForceLogout?.(reason);
          return;
        }
        case commandTypes.chatSend: {
          const pending = pendingRequests.get(message.requestId);
          pending?.resolve();
          pendingRequests.delete(message.requestId);
          callbacks.onSendAck?.(message.body as Record<string, unknown>);
          return;
        }
        case commandTypes.chatRecv:
          callbacks.onInboundMessage?.(message.body as Record<string, unknown>);
          return;
        case commandTypes.error: {
          const pending = pendingRequests.get(message.requestId);
          if (pending != null) {
            pending.reject(
              classifySendMessageError(
                extractBodyMessage(message.body, 'gateway error'),
                message.command,
              ),
            );
            pendingRequests.delete(message.requestId);
            return;
          }
          if (pendingAuthRequestId === message.requestId) {
            handleAuthFailure(message);
            return;
          }
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
    async sendMessage(requestId, payload) {
      if (socket == null) {
        throw new Error('WebSocket is not connected.');
      }
      const message = buildSendMessageRequest(requestId, payload);
      const pending = new Promise<void>((resolve, reject) => {
        pendingRequests.set(requestId, { resolve, reject });
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
