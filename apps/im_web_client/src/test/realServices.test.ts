import { afterEach, describe, expect, it, vi } from 'vitest';

import { createRealAuthService } from '../services/realAuthService';
import { createRealChatService } from '../services/realChatService';
import { createRealGatewayClient } from '../services/realGatewayClient';
import {
  buildAuthRequest,
  buildConnectRequest,
  commandTypes,
} from '../transport/envelope';

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
      socialBaseUrl: 'http://localhost:18085',
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

  it('lists friends, lists incoming and outgoing requests, sends, accepts, rejects, cancels, and starts a direct conversation through real endpoints', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse([{ userId: 'u_design', displayName: 'Mina Park', avatarSeed: 'MP' }]),
      )
      .mockResolvedValueOnce(
        jsonResponse([{ userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM', direction: 'incoming', status: 'pending', requestMessage: 'hey' }]),
      )
      .mockResolvedValueOnce(
        jsonResponse([{ userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV', direction: 'outgoing', status: 'pending', requestMessage: 'ping' }]),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV', direction: 'outgoing', status: 'pending', requestMessage: 'hello' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_reject', displayName: 'June Hale', avatarSeed: 'JH', direction: 'incoming', status: 'rejected' }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV', direction: 'outgoing', status: 'cancelled' }),
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
      socialBaseUrl: 'http://localhost:18085',
      imBaseUrl: 'http://localhost:18082',
    });

    const friends = await service.listFriends(createSession());
    const incoming = await service.listIncomingFriendRequests(createSession());
    const outgoing = await service.listOutgoingFriendRequests(createSession());
    const pending = await service.sendFriendRequest('u_ops', 'hello', createSession());
    const accepted = await service.acceptFriendRequest('u_editor', createSession());
    const rejected = await service.rejectFriendRequest('u_reject', createSession());
    const cancelled = await service.cancelFriendRequest('u_ops', createSession());
    const conversation = await service.startDirectConversation('u_editor', createSession());

    expect(friends[0]).toMatchObject({ userId: 'u_design' });
    expect(incoming[0]).toMatchObject({ userId: 'u_editor', direction: 'incoming', status: 'pending' });
    expect(outgoing[0]).toMatchObject({ userId: 'u_ops', direction: 'outgoing', status: 'pending' });
    expect(pending).toMatchObject({ userId: 'u_ops', status: 'pending', requestMessage: 'hello' });
    expect(accepted).toMatchObject({ userId: 'u_editor' });
    expect(rejected).toMatchObject({ userId: 'u_reject', status: 'rejected' });
    expect(cancelled).toMatchObject({ userId: 'u_ops', status: 'cancelled' });
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
      socialBaseUrl: 'http://localhost:18085',
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

  it('normalizes history responses into oldest-to-newest order', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse([
        {
          serverMsgId: 'msg_2',
          clientMsgId: 'client_2',
          conversationId: 'conv_1',
          senderId: 'u_operator',
          receiverId: 'u_design',
          content: 'newer',
          contentType: 1,
          sequence: 11,
          createdAt: '2026-03-19T11:05:00Z',
        },
        {
          serverMsgId: 'msg_1',
          clientMsgId: 'client_1',
          conversationId: 'conv_1',
          senderId: 'u_design',
          receiverId: 'u_operator',
          content: 'older',
          contentType: 1,
          sequence: 10,
          createdAt: '2026-03-19T11:00:00Z',
        },
      ]),
    );
    vi.stubGlobal('fetch', fetchMock);

    const service = createRealChatService({
      socialBaseUrl: 'http://localhost:18085',
      imBaseUrl: 'http://localhost:18082',
    });

    const page = await service.getHistory('conv_1', null, createSession());

    expect(page.items.map((item) => item.serverId)).toEqual(['msg_1', 'msg_2']);
  });
});

describe('realGatewayClient', () => {
  it('builds envelope auth payloads with ticket-based auth', () => {
    expect(buildAuthRequest('op-auth-1', { ticket: 'wst_123' })).toEqual({
      command: commandTypes.auth,
      requestId: 'op-auth-1',
      body: {
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
    expect(JSON.parse(socket.sent[0] as string)).toEqual(
      buildConnectRequest('op-connect-00000001'),
    );

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: {},
      }),
    });
    expect(JSON.parse(socket.sent[1] as string)).toEqual(
      buildAuthRequest('op-auth-00000002', { ticket: 'wst_123' }),
    );

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator', connId: 'conn_123' },
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
      command: commandTypes.chatSend,
      requestId: 'op-send-00000003',
      body: {
        clientMsgID: 'local_1',
        recvID: 'u_design',
        content: 'Need the final mockups before noon.',
        contentType: 101,
        sessionType: 1,
      },
    });

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.chatSend,
        requestId: 'op-send-00000003',
        body: {
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

  it('preserves the server connId from the connect response through auth success', async () => {
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
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: { connId: 'conn_from_connect' },
      }),
    });
    expect(JSON.parse(socket.sent[1] as string)).toEqual(
      buildAuthRequest('op-auth-00000002', { ticket: 'wst_123' }),
    );

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator' },
      }),
    });

    await expect(connectPromise).resolves.toEqual(
      expect.objectContaining({
        connId: 'conn_from_connect',
        lifecycle: 'connected',
      }),
    );
  });

  it('rejects pending send acknowledgements when the server returns an error command', async () => {
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
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: {},
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator', connId: 'conn_123' },
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

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.error,
        requestId: 'op-send-00000003',
        body: 'permission denied',
      }),
    });

    await expect(sendPromise).rejects.toThrow('permission denied');
  });

  it('rejects the connect promise when the server returns an error for the connect request', async () => {
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
    expect(JSON.parse(socket.sent[0] as string)).toEqual(
      buildConnectRequest('op-connect-00000001'),
    );

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.error,
        requestId: 'op-connect-00000001',
        body: 'connect rejected',
      }),
    });

    await expect(connectPromise).rejects.toThrow('connect rejected');
  });

  it('publishes inbound message, friend refresh, force-logout, and disconnect events', async () => {
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
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: {},
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator', connId: 'conn_123' },
      }),
    });
    await connectPromise;

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.chatRecv,
        requestId: 'op-notify-1',
        body: {
          conversationId: 'c1:u_design:u_operator',
          serverMsgID: 'msg_888',
          clientMsgID: 'client_888',
          content: 'Incoming brief from design.',
          sendTime: 1710000000001,
          ext: {
            senderId: 'u_design',
            recvId: 'u_operator',
          },
        },
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.chatRecv,
        requestId: 'op-friend-1',
        body: {
          conversationId: 'friend:u_operator:u_editor',
          ext: {
            notificationType: 'friend_request_accepted',
            senderId: 'u_editor',
            recvId: 'u_operator',
          },
        },
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.forceLogout,
        requestId: 'system',
        body: { reason: 'logged out elsewhere' },
      }),
    });
    socket.emit('close');

    expect(received).toEqual([
      expect.objectContaining({
        type: 'messageReceived',
        message: expect.objectContaining({
          conversationId: 'c1:u_design:u_operator',
          serverId: 'msg_888',
          senderId: 'u_design',
          text: 'Incoming brief from design.',
          direction: 'incoming',
        }),
      }),
      {
        type: 'friendStateChanged',
      },
      {
        type: 'forceLogout',
        reason: 'logged out elsewhere',
      },
      {
        type: 'disconnected',
      },
    ]);
  });

  it('maps lower-camel dispatch payload fields from postoffice recv notifications', async () => {
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
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: {},
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator', connId: 'conn_123' },
      }),
    });
    await connectPromise;

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.chatRecv,
        requestId: 'msg_999',
        body: {
          conversationId: 'c1:u_design:u_operator',
          serverMsgId: 'msg_999',
          clientMsgId: 'local_999',
          content: 'Lower camel payload.',
          sendTime: 1710000000999,
          ext: {
            senderId: 'u_operator',
            recvId: 'u_design',
          },
        },
      }),
    });

    expect(received).toEqual([
      expect.objectContaining({
        type: 'messageReceived',
        message: expect.objectContaining({
          localId: 'local_999',
          serverId: 'msg_999',
          senderId: 'u_operator',
          direction: 'outgoing',
          text: 'Lower camel payload.',
        }),
      }),
    ]);
  });

  it('maps read receipts directed to the current user even when senderId is absent', async () => {
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
      data: JSON.stringify({
        command: commandTypes.connect,
        requestId: 'op-connect-00000001',
        body: {},
      }),
    });
    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.auth,
        requestId: 'op-auth-00000002',
        body: { userID: 'u_operator', connId: 'conn_123' },
      }),
    });
    await connectPromise;

    socket.emit('message', {
      data: JSON.stringify({
        command: commandTypes.chatRecv,
        requestId: 'op-read-1',
        body: {
          conversationId: 'c1:u_design:u_operator',
          clientMsgId: 'read_123',
          recvID: 'u_operator',
          content: '18',
          contentType: 2004,
        },
      }),
    });

    expect(received).toEqual([
      {
        type: 'read',
        conversationId: 'c1:u_design:u_operator',
        seq: 18,
        senderId: '',
        recipientId: 'u_operator',
        clientMsgId: 'read_123',
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
