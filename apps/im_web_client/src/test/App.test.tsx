import '@testing-library/jest-dom/vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../app/App';
import type { AppDependencies } from '../app/providers';
import type { AuthSession, GatewayConnection, GatewayEvent } from '../domain/types';
import { createConversationStore } from '../state/conversationStore';
import { createSessionStore } from '../state/sessionStore';

describe('App', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_IM_SERVICE_MODE', 'fake');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('logs in, exchanges a ws ticket, and enters the connected chat shell', async () => {
    const user = userEvent.setup();

    render(<App />);

    await user.clear(screen.getByLabelText(/account/i));
    await user.type(screen.getByLabelText(/account/i), 'operator@cheese.im');
    await user.clear(screen.getByLabelText(/password/i));
    await user.type(screen.getByLabelText(/password/i), 'Password123!');
    await user.clear(screen.getByLabelText(/device name/i));
    await user.type(screen.getByLabelText(/device name/i), 'Design Deck');
    await user.selectOptions(screen.getByLabelText(/platform/i), 'web');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/^connected$/i)).toBeInTheDocument();
    expect((await screen.findAllByText(/design ops/i)).length).toBeGreaterThan(0);
    expect(screen.getByText(/ws ticket issued/i)).toBeInTheDocument();
    expect(screen.getByText(/mock gateway/i)).toBeInTheDocument();
  });

  it('loads older messages for the active conversation', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);

    expect(await screen.findByText(/welcome to the relay desk/i)).toBeInTheDocument();

    await user.click(await screen.findByRole('button', { name: /^load older$/i }));

    expect(await screen.findByText(/yesterday's handoff is attached to the brief/i)).toBeInTheDocument();
  });

  it('sends a message and resolves it from sending to delivered', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);

    await user.type(screen.getByLabelText(/message input/i), 'Need the final mockups before noon.');
    await user.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText(/sending/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/delivered/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/need the final mockups before noon\./i)).toBeInTheDocument();
  });

  it('updates unread state when an inbound message arrives for another conversation', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);

    await act(async () => {
      dependencies.emit({
        type: 'messageReceived',
        message: {
          localId: 'inbound-1',
          serverId: 'msg-9001',
          conversationId: 'conv-release-watch',
          senderId: 'u_ops',
          senderDisplay: 'Theo Vale',
          direction: 'incoming',
          text: 'The release window just opened.',
          timestamp: Date.now(),
          status: 'received',
        },
      });
    });

    expect(await screen.findByText(/the release window just opened\./i)).toBeInTheDocument();
    expect(screen.getAllByText('1').length).toBeGreaterThan(0);
  });

  it('shows reconnecting state without dropping the active chat shell', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await act(async () => {
      dependencies.emit({ type: 'disconnected' });
    });

    expect(await screen.findByText(/^reconnecting$/i)).toBeInTheDocument();
    expect(screen.getByText(/websocket connection closed\./i)).toBeInTheDocument();
    expect(screen.getByLabelText(/message input/i)).toBeInTheDocument();
  });

  it('surfaces force logout in the shell and returns to sign-in mode', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await act(async () => {
      dependencies.emit({ type: 'forceLogout', reason: 'logged out elsewhere' });
    });

    expect(await screen.findByText(/session revoked/i)).toBeInTheDocument();
    expect(screen.getByText(/logged out elsewhere/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('accepts an incoming friend request and opens a new direct conversation', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.click(screen.getByRole('button', { name: /^accept$/i }));

    expect(await screen.findByText(/rae mercer/i)).toBeInTheDocument();
    expect(screen.getByText(/no messages yet/i)).toBeInTheDocument();
  });
});

async function signIn(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.clear(screen.getByLabelText(/account/i));
  await user.type(screen.getByLabelText(/account/i), 'operator@cheese.im');
  await user.clear(screen.getByLabelText(/password/i));
  await user.type(screen.getByLabelText(/password/i), 'Password123!');
  await user.click(screen.getByRole('button', { name: /sign in/i }));
  await screen.findByText(/^connected$/i);
  expect((await screen.findAllByText(/design ops/i)).length).toBeGreaterThan(0);
}

function createRealtimeTestDependencies(): AppDependencies & { emit(event: GatewayEvent): void } {
  const sessionStore = createSessionStore({
    environmentLabel: 'Realtime Harness',
    transportLabel: 'Realtime Gateway',
  });
  const conversationStore = createConversationStore();
  const listeners = new Set<(event: GatewayEvent) => void>();

  const authSession: AuthSession = {
    sessionId: 'sess_test',
    deviceId: 'dev_test',
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
      accessToken: 'atk_test',
      refreshToken: 'rtk_test',
      accessExpireAt: Date.now() + 30_000,
      refreshExpireAt: Date.now() + 60_000,
    },
  };

  const connection: GatewayConnection = {
    connId: 'conn_test',
    lifecycle: 'connected',
    transportLabel: 'Realtime Gateway',
  };

  return {
    sessionStore,
    conversationStore,
    authService: {
      async login() {
        return authSession;
      },
      async issueWsTicket() {
        return {
          ticket: 'wst_test',
          wsUrl: 'ws://localhost:5147/ws',
          expireAt: Date.now() + 60_000,
        };
      },
    },
    chatService: {
      async listConversations() {
        return [
          {
            conversationId: 'conv-design-ops',
            title: 'Design Ops',
            subtitle: 'Editorial channel · Product handoff',
            kind: 'DIRECT',
            peerUserId: 'u_design',
            lastMessagePreview: 'Welcome to the relay desk.',
            lastMessageTime: Date.now() - 1000,
            unreadCount: 0,
            accentColor: '#6ef1c6',
          },
          {
            conversationId: 'conv-release-watch',
            title: 'Release Watch',
            subtitle: 'Operations channel · Incident window',
            kind: 'DIRECT',
            peerUserId: 'u_ops',
            lastMessagePreview: 'QA sign-off is still pending.',
            lastMessageTime: Date.now() - 2000,
            unreadCount: 0,
            accentColor: '#8aa8ff',
          },
        ];
      },
      async listFriends() {
        return [
          { userId: 'u_design', displayName: 'Mina Park', avatarSeed: 'MP' },
          { userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV' },
        ];
      },
      async listIncomingFriendRequests() {
        return [
          { userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM', status: 'PENDING' as const },
        ];
      },
      async sendFriendRequest(friendUserId) {
        return {
          userId: friendUserId,
          displayName: friendUserId
            .split(/[._-]/g)
            .filter(Boolean)
            .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
            .join(' '),
          avatarSeed: 'UN',
          status: 'PENDING' as const,
        };
      },
      async acceptFriendRequest(friendUserId) {
        if (friendUserId === 'u_editor') {
          return {
            userId: friendUserId,
            displayName: 'Rae Mercer',
            avatarSeed: 'RM',
          };
        }
        return {
          userId: friendUserId,
          displayName: friendUserId
            .split(/[._-]/g)
            .filter(Boolean)
            .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
            .join(' '),
          avatarSeed: 'UN',
        };
      },
      async startDirectConversation(friendUserId) {
        return {
          conversationId: `single:${friendUserId}:u_operator`,
          title: friendUserId,
          subtitle: 'Direct conversation',
          kind: 'DIRECT',
          peerUserId: friendUserId,
          lastMessagePreview: 'No messages yet',
          lastMessageTime: Date.now(),
          unreadCount: 0,
          accentColor: '#79d7ff',
        };
      },
      async getHistory(conversationId) {
        if (conversationId === 'conv-design-ops') {
          return {
            items: [
              {
                localId: 'seed-1',
                serverId: 'seed-1',
                conversationId: 'conv-design-ops',
                senderId: 'u_design',
                senderDisplay: 'Mina Park',
                direction: 'incoming',
                text: 'Welcome to the relay desk.',
                timestamp: Date.now() - 3000,
                status: 'received',
              },
            ],
            nextCursor: null,
            hasMore: false,
          };
        }
        return {
          items: [],
          nextCursor: null,
          hasMore: false,
        };
      },
    },
    gatewayClient: {
      async connect() {
        return connection;
      },
      async sendText() {
        return {
          serverId: 'msg_sent',
          sentAt: Date.now(),
        };
      },
      subscribe(listener) {
        listeners.add(listener);
        return () => {
          listeners.delete(listener);
        };
      },
    },
    emit(event: GatewayEvent) {
      listeners.forEach((listener) => listener(event));
    },
  };
}
