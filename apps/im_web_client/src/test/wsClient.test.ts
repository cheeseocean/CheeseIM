import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  buildAuthRequest,
  buildConnectRequest,
  buildSendMessageRequest,
} from '../transport/wsMessage';
import { createSessionStore } from '../state/sessionStore';
import { createWsClient } from '../transport/wsClient';

afterEach(() => {
  vi.useRealTimers();
});

describe('wsMessage', () => {
  it('serializes connect requests in postoffice WSMessage shape', () => {
    const payload = buildConnectRequest('op-connect-1');

    expect(payload.msgType).toBe(1001);
    expect(payload.operationID).toBe('op-connect-1');
  });

  it('serializes auth requests in postoffice WSMessage shape', () => {
    const payload = buildAuthRequest('op-auth-1', {
      token: 'jwt',
      userID: 'u1',
      platformID: 5,
    });

    expect(payload.msgType).toBe(1101);
    expect(payload.data.userID).toBe('u1');
  });

  it('serializes send requests in postoffice WSMessage shape', () => {
    const payload = buildSendMessageRequest('op-send-1', {
      clientMsgID: 'client-1',
      recvID: 'u2',
      content: 'hello',
      contentType: 1,
      sessionType: 1,
    });

    expect(payload.msgType).toBe(2001);
    expect(payload.data.clientMsgID).toBe('client-1');
    expect(payload.data.recvID).toBe('u2');
  });
});

describe('sessionStore', () => {
  it('moves from sign-in to ticket issuance after authentication', () => {
    const store = createSessionStore();

    store.startSignIn({
      account: 'operator@cheese.im',
      password: 'Password123!',
      deviceName: 'Studio Browser',
      platform: 'web',
    });
    store.setAuthenticated({
      sessionId: 'sess-1',
      deviceId: 'dev-1',
      platform: 'web',
      deviceName: 'Studio Browser',
      profile: {
        userId: 'u_operator',
        displayName: 'Avery Stone',
        title: 'Relay Operator',
        tenantName: 'Cheese Ocean Studio',
        avatarSeed: 'AS',
      },
      tokens: {
        accessToken: 'atk',
        refreshToken: 'rtk',
        accessExpireAt: 100,
        refreshExpireAt: 200,
      },
    });

    expect(store.getState().stage).toBe('issuing_ticket');
    expect(store.getState().statusLabel).toBe('Session ready');
  });

  it('transitions to connecting after a ws ticket is issued', () => {
    const store = createSessionStore();

    store.setTicket({
      ticket: 'wst-1',
      wsUrl: 'wss://mock-gateway.local/ws',
      expireAt: 100,
    });

    expect(store.getState().stage).toBe('connecting');
    expect(store.getState().lifecycle).toBe('connecting');
    expect(store.getState().ticketStatusLabel).toBe('WS ticket issued');
  });

  it('records a connected gateway session', () => {
    const store = createSessionStore();

    store.setConnected({
      connId: 'conn-1',
      lifecycle: 'connected',
      transportLabel: 'Mock Gateway',
    });

    expect(store.getState().stage).toBe('connected');
    expect(store.getState().lifecycle).toBe('connected');
    expect(store.getState().statusLabel).toBe('Connected');
  });
});

describe('wsClient', () => {
  it('sends auth after receiving connect success and notifies ready after auth success', () => {
    const sent: string[] = [];
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const onReady = vi.fn();
    const socket = {
      send(payload: string) {
        sent.push(payload);
      },
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(() => socket, { onReady });

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    listeners.get('open')?.();
    listeners.get('message')?.({
      data: JSON.stringify({ msgType: 1002, operationID: 'system', data: '连接成功' }),
    });
    listeners.get('message')?.({
      data: JSON.stringify({
        msgType: 1102,
        operationID: 'op-auth-1',
        data: { userID: 'u1', message: '认证成功' },
      }),
    });

    expect(sent).toHaveLength(2);
    expect(JSON.parse(sent[0]).msgType).toBe(1001);
    expect(JSON.parse(sent[1]).msgType).toBe(1101);
    expect(onReady).toHaveBeenCalledTimes(1);
  });

  it('sends heartbeat requests on an interval after auth success', () => {
    vi.useFakeTimers();

    const sent: string[] = [];
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const socket = {
      send(payload: string) {
        sent.push(payload);
      },
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(
      () => socket,
      {},
      { heartbeatIntervalMs: 10 },
    );

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    listeners.get('open')?.();
    listeners.get('message')?.({
      data: JSON.stringify({ msgType: 1002, operationID: 'system', data: '连接成功' }),
    });
    listeners.get('message')?.({
      data: JSON.stringify({
        msgType: 1102,
        operationID: 'op-auth-1',
        data: { userID: 'u1', message: '认证成功' },
      }),
    });

    vi.advanceTimersByTime(25);

    const heartbeatMessages = sent
      .map((payload) => JSON.parse(payload) as { msgType: number })
      .filter((message) => message.msgType === 1201);

    expect(heartbeatMessages.length).toBeGreaterThanOrEqual(2);
  });

  it('reconnects with the previous session after socket close', () => {
    vi.useFakeTimers();

    const sockets: Array<{
      send(payload: string): void;
      close(): void;
      addEventListener(type: string, listener: (event?: { data?: string }) => void): void;
    }> = [];
    const listenersBySocket: Array<Map<string, (event?: { data?: string }) => void>> = [];

    const client = createWsClient(
      () => {
        const listeners = new Map<string, (event?: { data?: string }) => void>();
        listenersBySocket.push(listeners);
        const socket = {
          send: vi.fn(),
          close: vi.fn(),
          addEventListener(type: string, listener: (event?: { data?: string }) => void) {
            listeners.set(type, listener);
          },
        };
        sockets.push(socket);
        return socket as unknown as WebSocket;
      },
      {},
      { reconnectDelaysMs: [10] },
    );

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    expect(sockets).toHaveLength(1);

    listenersBySocket[0].get('close')?.();
    vi.advanceTimersByTime(10);

    expect(sockets).toHaveLength(2);
  });

  it('stops reconnecting and notifies force logout on force logout', () => {
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const onForceLogout = vi.fn();
    const socket = {
      send: vi.fn(),
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(() => socket, { onForceLogout });

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    listeners.get('message')?.({
      data: JSON.stringify({
        msgType: 7002,
        operationID: 'system',
        data: { reason: 'logged out elsewhere' },
      }),
    });

    expect(onForceLogout).toHaveBeenCalledWith('logged out elsewhere');
  });

  it('resolves sendMessage when send acknowledgement arrives for the same operation', async () => {
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const socket = {
      send: vi.fn(),
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(() => socket);

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    const sendPromise = client.sendMessage('op-send-000001', {
      clientMsgID: 'client-1',
      recvID: 'u2',
      content: 'hello',
      contentType: 1,
      sessionType: 1,
    });

    listeners.get('message')?.({
      data: JSON.stringify({
        msgType: 2002,
        operationID: 'op-send-000001',
        data: {
          clientMsgID: 'client-1',
          serverMsgID: 'server-1',
          sendTime: 1710000000000,
        },
      }),
    });

    await expect(sendPromise).resolves.toBeUndefined();
  });

  it('rejects sendMessage on permission error for the same operation', async () => {
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const socket = {
      send: vi.fn(),
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(() => socket);

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    const sendPromise = client.sendMessage('op-send-000001', {
      clientMsgID: 'client-1',
      recvID: 'u2',
      content: 'hello',
      contentType: 1,
      sessionType: 1,
    });

    listeners.get('message')?.({
      data: JSON.stringify({
        msgType: 9003,
        operationID: 'op-send-000001',
        data: 'permission denied',
      }),
    });

    await expect(sendPromise).rejects.toMatchObject({
      kind: 'permissionDenied',
      message: 'permission denied',
    });
  });

  it('rejects sendMessage with connectionLost when the socket closes', async () => {
    const listeners = new Map<string, (event?: { data?: string }) => void>();
    const socket = {
      send: vi.fn(),
      close: vi.fn(),
      addEventListener(type: string, listener: (event?: { data?: string }) => void) {
        listeners.set(type, listener);
      },
    } as unknown as WebSocket;
    const client = createWsClient(() => socket);

    client.connect({
      wsUrl: 'ws://localhost:5147/ws',
      userID: 'u1',
      platformID: 5,
      token: 'jwt',
    });

    const sendPromise = client.sendMessage('op-send-000001', {
      clientMsgID: 'client-1',
      recvID: 'u2',
      content: 'hello',
      contentType: 1,
      sessionType: 1,
    });

    listeners.get('close')?.();

    await expect(sendPromise).rejects.toMatchObject({
      kind: 'connectionLost',
      message: 'WebSocket connection closed.',
    });
  });
});
