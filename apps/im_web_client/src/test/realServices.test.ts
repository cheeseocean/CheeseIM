import { afterEach, describe, expect, it, vi } from 'vitest';

import { buildWsTicketAuthRequest, createRealGatewayClient } from '../services/realGatewayClient';
import { createRealAuthService } from '../services/realAuthService';
import { createRealChatService } from '../services/realChatService';

describe('realAuthService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps authcenter login and ws-ticket responses into client models', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse({
          userId: 'u_operator',
          sessionId: 'sess_123',
          accessToken: 'atk_123',
          refreshToken: 'rtk_123',
          accessExpireAt: 1000,
          refreshExpireAt: 2000,
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          ticket: 'wst_123',
          expire_at: 3000,
          ws_url: '/ws',
        }),
      );

    vi.stubGlobal('fetch', fetchMock);

    const service = createRealAuthService({
      authBaseUrl: '',
      wsUrl: 'ws://localhost:5147/ws',
    });

    const session = await service.login({
      account: 'operator@cheese.im',
      password: 'Password123!',
      deviceName: 'Studio Browser',
      platform: 'web',
    });
    const ticket = await service.issueWsTicket(session);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
      }),
    );
    expect(session.sessionId).toBe('sess_123');
    expect(session.profile.userId).toBe('u_operator');
    expect(ticket).toMatchObject({
      ticket: 'wst_123',
      expireAt: 3000,
      wsUrl: 'ws://localhost:5147/ws',
    });
  });
});

describe('realChatService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads conversation summaries from postbox', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse([
        {
          conversationId: 'single:u_design:u_operator',
          title: 'u_design',
          subtitle: 'Direct conversation',
          kind: 'DIRECT',
          peerUserId: 'u_design',
          lastMessagePreview: 'Welcome to the relay desk.',
          lastMessageTime: 1710000000000,
          unreadCount: 2,
          accentColor: '#6ef1c6',
        },
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    const service = createRealChatService({
      authBaseUrl: 'http://localhost:18084',
      imBaseUrl: 'http://localhost:18082',
    });

    const conversations = await service.listConversations(createSession());

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:18082/api/im/conversations?limit=20',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer atk_123',
        }),
      }),
    );
    expect(conversations).toEqual([
      expect.objectContaining({
        conversationId: 'single:u_design:u_operator',
        title: 'u_design',
        peerUserId: 'u_design',
        lastMessagePreview: 'Welcome to the relay desk.',
        unreadCount: 2,
      }),
    ]);
  });

  it('lists friends, lists requests, sends a request, accepts it, and starts a direct conversation through real endpoints', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse([{ userId: 'u_design', displayName: 'Mina Park', avatarSeed: 'MP' }]),
      )
      .mockResolvedValueOnce(
        jsonResponse([{ userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM', status: 'PENDING' }]),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV', status: 'PENDING' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          conversationId: 'single:u_editor:u_operator',
          title: 'u_editor',
          subtitle: 'Direct conversation',
          kind: 'DIRECT',
          peerUserId: 'u_editor',
          lastMessagePreview: 'No messages yet',
          lastMessageTime: 1710000000000,
          unreadCount: 0,
          accentColor: '#8aa8ff',
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    const service = createRealChatService({
      authBaseUrl: 'http://localhost:18084',
      imBaseUrl: 'http://localhost:18082',
    });

    const friends = await service.listFriends(createSession());
    const requests = await service.listIncomingFriendRequests(createSession());
    const pending = await service.sendFriendRequest('u_ops', createSession());
    const accepted = await service.acceptFriendRequest('u_editor', createSession());
    const conversation = await service.startDirectConversation('u_editor', createSession());

    expect(friends[0]).toMatchObject({ userId: 'u_design' });
    expect(requests[0]).toMatchObject({ userId: 'u_editor', status: 'PENDING' });
    expect(pending).toMatchObject({ userId: 'u_ops', status: 'PENDING' });
    expect(accepted).toMatchObject({ userId: 'u_editor' });
    expect(conversation).toMatchObject({
      conversationId: 'single:u_editor:u_operator',
      peerUserId: 'u_editor',
    });
  });

  it('maps history responses from postbox into message items', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse([
        {
          serverMsgId: 'msg_1',
          clientMsgId: 'client_1',
          conversationId: 'conv_1',
          senderId: 'u_design',
          receiverId: 'u_operator',
          content: 'Welcome to the relay desk.',
          contentType: 1,
          sequence: 10,
          createdAt: '2026-03-19T11:00:00Z',
        },
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    const service = createRealChatService({
      authBaseUrl: 'http://localhost:18084',
      imBaseUrl: 'http://localhost:18082',
    });

    const page = await service.getHistory(
      'conv_1',
      null,
      {
        sessionId: 'sess_123',
        deviceId: 'dev_123',
        deviceName: 'Studio Browser',
        platform: 'web',
        profile: {
          userId: 'u_operator',
          displayName: 'Avery Stone',
          title: 'Relay Operator',
          tenantName: 'Cheese Ocean Studio',
          avatarSeed: 'AS',
        },
        tokens: {
          accessToken: 'atk_123',
          refreshToken: 'rtk_123',
          accessExpireAt: 1000,
          refreshExpireAt: 2000,
        },
      },
    );

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:18082/api/im/conversations/conv_1/messages?limit=20',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer atk_123',
        }),
      }),
    );
    expect(page.items[0]).toMatchObject({
      serverId: 'msg_1',
      conversationId: 'conv_1',
      senderId: 'u_design',
      text: 'Welcome to the relay desk.',
      direction: 'incoming',
    });
  });
});

describe('realGatewayClient', () => {
  it('builds ws auth payloads with ticket-based auth', () => {
    expect(buildWsTicketAuthRequest('op-auth-1', 'wst_123')).toEqual({
      msgType: 1101,
      operationID: 'op-auth-1',
      data: {
        ticket: 'wst_123',
      },
    });
  });

  it('connects, authenticates with ticket, and resolves send acknowledgements', async () => {
    const socket = createMockSocket();
    const client = createRealGatewayClient({
      socketFactory: () => socket.instance,
    });

    const connectPromise = client.connect(
      {
        ticket: 'wst_123',
        expireAt: 3000,
        wsUrl: 'ws://localhost:5147/ws',
      },
      createSession(),
    );

    socket.emit('open');
    expect(JSON.parse(socket.sent[0] as string)).toMatchObject({ msgType: 1001 });

    socket.emit('message', {
      data: JSON.stringify({ msgType: 1002, operationID: 'system', data: '连接成功' }),
    });
    expect(JSON.parse(socket.sent[1] as string)).toEqual(
      buildWsTicketAuthRequest('op-auth-00000001', 'wst_123'),
    );

    socket.emit('message', {
      data: JSON.stringify({
        msgType: 1102,
        operationID: 'op-auth-00000001',
        data: { userID: 'u_operator', connId: 'conn_123' },
      }),
    });

    await expect(connectPromise).resolves.toMatchObject({
      connId: 'conn_123',
      lifecycle: 'connected',
    });

    const sendPromise = client.sendText({
      conversationId: 'conv_design_ops',
      recipientId: 'u_design',
      text: 'Need the final mockups before noon.',
      localId: 'local_1',
      session: createSession(),
    });

    const sendOperation = JSON.parse(socket.sent[2] as string);
    expect(sendOperation).toMatchObject({
      msgType: 2001,
      data: {
        clientMsgID: 'local_1',
        recvID: 'u_design',
        content: 'Need the final mockups before noon.',
      },
    });

    socket.emit('message', {
      data: JSON.stringify({
        msgType: 2002,
        operationID: sendOperation.operationID,
        data: {
          serverMsgID: 'msg_123',
          clientMsgID: 'local_1',
          sendTime: 1710000000000,
        },
      }),
    });

    await expect(sendPromise).resolves.toEqual({
      serverId: 'msg_123',
      sentAt: 1710000000000,
    });
  });

  it('publishes inbound message, force-logout, and disconnect events', async () => {
    const socket = createMockSocket();
    const client = createRealGatewayClient({
      socketFactory: () => socket.instance,
    });
    const received: unknown[] = [];

    client.subscribe((event) => {
      received.push(event);
    });

    const connectPromise = client.connect(
      {
        ticket: 'wst_123',
        expireAt: 3000,
        wsUrl: 'ws://localhost:5147/ws',
      },
      createSession(),
    );

    socket.emit('open');
    socket.emit('message', {
      data: JSON.stringify({ msgType: 1002, operationID: 'system', data: '连接成功' }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        msgType: 1102,
        operationID: 'op-auth-00000001',
        data: { userID: 'u_operator', connId: 'conn_123' },
      }),
    });
    await connectPromise;

    socket.emit('message', {
      data: JSON.stringify({
        msgType: 2003,
        operationID: 'op-notify-1',
        data: {
          serverMsgID: 'msg_888',
          clientMsgID: 'client_888',
          sendID: 'u_design',
          recvID: 'u_operator',
          content: 'Incoming brief from design.',
          sendTime: 1710000000001,
        },
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        msgType: 7002,
        operationID: 'system',
        data: { reason: 'logged out elsewhere' },
      }),
    });
    socket.emit('close');

    expect(received).toEqual([
      expect.objectContaining({
        type: 'messageReceived',
        message: expect.objectContaining({
          serverId: 'msg_888',
          senderId: 'u_design',
          text: 'Incoming brief from design.',
          direction: 'incoming',
        }),
      }),
      {
        type: 'forceLogout',
        reason: 'logged out elsewhere',
      },
      {
        type: 'disconnected',
      },
    ]);
  });
});

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response;
}

function createSession() {
  return {
    sessionId: 'sess_123',
    deviceId: 'dev_123',
    deviceName: 'Studio Browser',
    platform: 'web' as const,
    profile: {
      userId: 'u_operator',
      displayName: 'Avery Stone',
      title: 'Relay Operator',
      tenantName: 'Cheese Ocean Studio',
      avatarSeed: 'AS',
    },
    tokens: {
      accessToken: 'atk_123',
      refreshToken: 'rtk_123',
      accessExpireAt: 1000,
      refreshExpireAt: 2000,
    },
  };
}

function createMockSocket() {
  const listeners = new Map<string, Array<(event?: { data?: string }) => void>>();
  const sent: string[] = [];
  const instance = {
    readyState: 1,
    send(payload: string) {
      sent.push(payload);
    },
    close: vi.fn(),
    addEventListener(type: string, listener: (event?: { data?: string }) => void) {
      const current = listeners.get(type) ?? [];
      current.push(listener);
      listeners.set(type, current);
    },
  } as unknown as WebSocket;

  return {
    instance,
    sent,
    emit(type: string, event?: { data?: string }) {
      for (const listener of listeners.get(type) ?? []) {
        listener(event);
      }
    },
  };
}
