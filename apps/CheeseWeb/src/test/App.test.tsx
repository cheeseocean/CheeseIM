import '@testing-library/jest-dom/vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../app/App';
import type { AppDependencies } from '../app/providers';
import type { AuthSession, GatewayConnection } from '../domain/types';
import type { GatewayEvent } from '../services/contracts';
import { createConversationStore } from '../state/conversationStore';
import { createSessionStore } from '../state/sessionStore';

describe('App', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_IM_SERVICE_MODE', 'fake');
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    window.localStorage.clear();
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
    expect(
      screen.getByText((_, element) => element?.textContent?.replace(/\s+/g, ' ').trim() === 'CheeseIM Chat'),
    ).toBeInTheDocument();
    expect(document.querySelector('.header-user-name')?.textContent?.trim()).toBe('Avery Stone');
    expect(screen.getByRole('button', { name: /log out/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /conversations/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /friends/i })).toBeInTheDocument();
  });

  it('restores a persisted session after page refresh and re-enters the chat shell', async () => {
    const persistedSession: AuthSession = {
      sessionId: 'sess_restore',
      deviceId: 'dev_restore',
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
        accessToken: 'atk_restore',
        refreshToken: 'rtk_restore',
        accessExpireAt: Date.now() + 30_000,
        refreshExpireAt: Date.now() + 60_000,
      },
    };
    window.localStorage.setItem('cheeseim.web.auth-session', JSON.stringify(persistedSession));

    render(<App />);

    expect(await screen.findByText(/^connected$/i)).toBeInTheDocument();
    expect((await screen.findAllByText(/design ops/i)).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument();
  });

  it('keeps the active session when switching theme', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);
    await user.click(screen.getByRole('button', { name: /theme switch/i }));

    expect(await screen.findByText(/^connected$/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument();
    expect((await screen.findAllByText(/design ops/i)).length).toBeGreaterThan(0);
  });

  it('loads older messages for the active conversation', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);

    expect(await screen.findByText(/welcome to the relay desk/i)).toBeInTheDocument();
    expect(screen.getByText(/active conversation/i)).toBeInTheDocument();

    await user.click(await screen.findByRole('button', { name: /^load older$/i }));

    expect(await screen.findByText(/yesterday's handoff is attached to the brief/i)).toBeInTheDocument();
  });

  it('clears only the current conversation messages from the page', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);

    const messageList = document.querySelector('.message-list');
    expect(messageList).not.toBeNull();
    await waitFor(() => {
      expect(messageList?.textContent ?? '').toMatch(/welcome to the relay desk/i);
    });
    await user.click(screen.getByRole('button', { name: /^clear$/i }));

    await waitFor(() => {
      expect(messageList?.textContent ?? '').not.toMatch(/welcome to the relay desk/i);
    });
    expect(screen.getAllByText(/design ops/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/history fully loaded/i)).toBeInTheDocument();
  });

  it('sends a message and resolves it from sending to delivered', async () => {
    const user = userEvent.setup();

    render(<App />);

    await signIn(user);

    await user.type(screen.getByLabelText(/message input/i), 'Need the final mockups before noon.{enter}');

    expect(await screen.findByText(/sending/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/delivered/i)).toBeInTheDocument();
    });
    expect(screen.getAllByText(/need the final mockups before noon\./i).length).toBeGreaterThan(0);
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

  it('confirms read when the active conversation receives a new message', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);

    await act(async () => {
      dependencies.emit({
        type: 'messageReceived',
        message: {
          localId: 'inbound-read-1',
          serverId: 'msg-read-1',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          direction: 'incoming',
          text: 'Please review the latest layout.',
          timestamp: Date.now(),
          status: 'received',
          seq: 12,
        },
      });
    });

    await waitFor(() => {
      expect(
        dependencies
          .sentSignals()
          .some((signal) => signal.contentType === 2004 && signal.conversationId === 'conv-design-ops' && signal.content === '12'),
      ).toBe(true);
    });
  });

  it('does not treat the local read cursor echo as a peer read receipt', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.type(screen.getByLabelText(/message input/i), 'Please confirm receipt.{enter}');
    await screen.findByText(/delivered/i);

    await act(async () => {
      dependencies.emit({
        type: 'read',
        conversationId: 'conv-design-ops',
        seq: 999,
        senderId: 'u_operator',
        recipientId: 'u_design',
        clientMsgId: 'read_local_echo',
      });
    });

    expect(screen.queryByText(/^read$/i)).not.toBeInTheDocument();
    expect(screen.getByText(/delivered/i)).toBeInTheDocument();
  });

  it('ignores read events that do not identify a remote sender', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.type(screen.getByLabelText(/message input/i), 'Read receipt source must be explicit.{enter}');
    await screen.findByText(/delivered/i);

    await act(async () => {
      dependencies.emit({
        type: 'read',
        conversationId: 'conv-design-ops',
        seq: 999,
        senderId: '',
        recipientId: '',
        clientMsgId: 'read_unknown',
      });
    });

    expect(screen.queryByText(/^read$/i)).not.toBeInTheDocument();
    expect(screen.getByText(/delivered/i)).toBeInTheDocument();
  });

  it('marks my outgoing messages as read when the receipt is directed to the current user', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.type(screen.getByLabelText(/message input/i), 'The recipient actually read this.{enter}');
    await screen.findByText(/delivered/i);

    await act(async () => {
      dependencies.emit({
        type: 'read',
        conversationId: 'conv-design-ops',
        seq: 999,
        senderId: '',
        recipientId: 'u_operator',
        clientMsgId: 'read_remote_1',
      });
    });

    expect(await screen.findByText(/^read$/i)).toBeInTheDocument();
  });

  it('marks my outgoing messages as read from a real 2004 payload shape with only clientMsgId and conversationId', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.type(screen.getByLabelText(/message input/i), 'Real payload should still mark read.{enter}');
    await screen.findByText(/delivered/i);

    await act(async () => {
      dependencies.emit({
        type: 'read',
        conversationId: 'c1:u_design:u_operator',
        seq: 277,
        senderId: '',
        recipientId: '',
        clientMsgId: 'read_1774251220652',
      });
    });

    expect(await screen.findByText(/^read$/i)).toBeInTheDocument();
  });

  it('does not confirm read while the page is hidden, but confirms after switching back to the conversation', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();
    const originalVisibility = Object.getOwnPropertyDescriptor(document, 'visibilityState');

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'hidden',
    });

    try {
      render(<App dependencies={dependencies} />);

      await signIn(user);

      await act(async () => {
        dependencies.emit({
          type: 'messageReceived',
          message: {
            localId: 'inbound-hidden-1',
            serverId: 'msg-hidden-1',
            conversationId: 'conv-design-ops',
            senderId: 'u_design',
            senderDisplay: 'Mina Park',
            direction: 'incoming',
            text: 'Hidden tab should not mark this as read.',
            timestamp: Date.now(),
            status: 'received',
            seq: 13,
          },
        });
      });

      expect(
        dependencies
          .sentSignals()
          .some((signal) => signal.contentType === 2004 && signal.conversationId === 'conv-design-ops' && signal.content === '13'),
      ).toBe(false);

      Object.defineProperty(document, 'visibilityState', {
        configurable: true,
        get: () => 'visible',
      });

      await user.click(screen.getByRole('button', { name: /release watch/i }));
      await user.click(screen.getByRole('button', { name: /design ops/i }));

      await waitFor(() => {
        expect(
          dependencies
            .sentSignals()
            .some((signal) => signal.contentType === 2004 && signal.conversationId === 'conv-design-ops' && signal.content === '13'),
        ).toBe(true);
      });
    } finally {
      if (originalVisibility == null) {
        delete (document as Document & { visibilityState?: DocumentVisibilityState }).visibilityState;
      } else {
        Object.defineProperty(document, 'visibilityState', originalVisibility);
      }
    }
  });

  it('confirms read for realtime-created c1 conversations that do not have peerUserId metadata', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);

    await act(async () => {
      dependencies.emit({
        type: 'messageReceived',
        message: {
          localId: 'inbound-c1-1',
          serverId: 'msg-c1-1',
          conversationId: 'c1:u_design:u_operator',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          direction: 'incoming',
          text: 'Please acknowledge this thread.',
          timestamp: Date.now(),
          status: 'received',
          seq: 21,
        },
      });
    });

    const conversationList = document.querySelector('.conversation-list');
    expect(conversationList).not.toBeNull();
    await user.click(within(conversationList as HTMLElement).getByRole('button', { name: /mina park/i }));

    await waitFor(() => {
      expect(
        dependencies
          .sentSignals()
          .some((signal) => signal.contentType === 2004 && signal.conversationId === 'c1:u_design:u_operator' && signal.content === '21'),
      ).toBe(true);
    });
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

    expect(await screen.findByRole('button', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getAllByText(/session revoked/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/logged out elsewhere/i)).toBeInTheDocument();
    expect(screen.getAllByText(/sign in to cheeseim chat/i).length).toBeGreaterThan(0);
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

  it('sends a friend request into outgoing and can cancel it', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.click(screen.getByRole('button', { name: /friends/i }));
    await user.type(screen.getByLabelText(/^send request$/i), 'u_ops');
    await user.click(screen.getByRole('button', { name: /^request$/i }));

    expect(await screen.findAllByText(/theo vale/i)).not.toHaveLength(0);
    expect(screen.getByRole('button', { name: /^cancel$/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /^cancel$/i })).not.toBeInTheDocument();
    });
  });

  it('rejects an incoming friend request', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    await user.click(screen.getByRole('button', { name: /friends/i }));
    await user.click(screen.getByRole('button', { name: /^reject$/i }));

    await waitFor(() => {
      expect(screen.queryByText(/rae mercer/i)).not.toBeInTheDocument();
    });
  });

  it('refreshes friend state when a realtime friend notification arrives', async () => {
    const user = userEvent.setup();
    const dependencies = createRealtimeTestDependencies();

    render(<App dependencies={dependencies} />);

    await signIn(user);
    expect(dependencies.friendSnapshotCalls()).toBe(1);

    await act(async () => {
      dependencies.emit({ type: 'friendStateChanged' });
    });

    await waitFor(() => {
      expect(dependencies.friendSnapshotCalls()).toBe(2);
    });
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

function createRealtimeTestDependencies(): AppDependencies & {
  emit(event: GatewayEvent): void;
  sentSignals(): Array<{ conversationId: string; contentType: number; content: string }>;
  friendSnapshotCalls(): number;
} {
  const sessionStore = createSessionStore({
    environmentLabel: 'Realtime Harness',
    transportLabel: 'Realtime Gateway',
  });
  const conversationStore = createConversationStore();
  const listeners = new Set<(event: GatewayEvent) => void>();
  let friendSnapshotCalls = 0;
  const sentSignals: Array<{ conversationId: string; contentType: number; content: string }> = [];

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
        friendSnapshotCalls += 1;
        return [
          { userId: 'u_design', displayName: 'Mina Park', avatarSeed: 'MP' },
          { userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV' },
        ];
      },
      async listIncomingFriendRequests() {
        return [
          {
            userId: 'u_editor',
            displayName: 'Rae Mercer',
            avatarSeed: 'RM',
            direction: 'incoming' as const,
            status: 'pending' as const,
            requestMessage: 'Please add me',
          },
        ];
      },
      async listOutgoingFriendRequests() {
        return [];
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
          direction: 'outgoing' as const,
          status: 'pending' as const,
          requestMessage: null,
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
      async rejectFriendRequest(friendUserId) {
        return {
          userId: friendUserId,
          displayName: 'Rae Mercer',
          avatarSeed: 'RM',
          direction: 'incoming' as const,
          status: 'rejected' as const,
          requestMessage: null,
        };
      },
      async cancelFriendRequest(friendUserId) {
        return {
          userId: friendUserId,
          displayName: 'Theo Vale',
          avatarSeed: 'TV',
          direction: 'outgoing' as const,
          status: 'cancelled' as const,
          requestMessage: null,
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
      async sendMessage() {
        sentSignals.push({
          conversationId: arguments[0].conversationId,
          contentType: arguments[0].contentType,
          content: arguments[0].content,
        });
        return {
          serverId: 'msg_signal',
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
    friendSnapshotCalls() {
      return friendSnapshotCalls;
    },
    sentSignals() {
      return sentSignals;
    },
    emit(event: GatewayEvent) {
      listeners.forEach((listener) => listener(event));
    },
  };
}
