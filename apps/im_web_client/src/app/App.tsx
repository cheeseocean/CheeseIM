import React from 'react';

import type {
  AuthSession,
  FriendRequestSummary,
  FriendSummary,
  LoginCredentials,
  MessageItem,
} from '../domain/types';
import { LoginView } from '../features/auth/LoginView';
import { ChatLayout } from '../features/chat/ChatLayout';
import { formatSendError } from '../features/chat/sendError';
import { AppProviders, createDefaultDependencies, type AppDependencies } from './providers';

export function App({ dependencies = createDefaultDependencies() }: { dependencies?: AppDependencies }) {
  return (
    <AppProviders dependencies={dependencies}>
      <AppBody dependencies={dependencies} />
    </AppProviders>
  );
}

function AppBody({ dependencies }: { dependencies: AppDependencies }) {
  const [friends, setFriends] = React.useState<FriendSummary[]>([]);
  const [incomingRequests, setIncomingRequests] = React.useState<FriendRequestSummary[]>([]);
  const session = React.useSyncExternalStore(
    dependencies.sessionStore.subscribe,
    dependencies.sessionStore.getState,
  );
  const conversationState = React.useSyncExternalStore(
    dependencies.conversationStore.subscribe,
    dependencies.conversationStore.getState,
  );

  const activeConversationId = conversationState.activeConversationId;
  const messages =
    activeConversationId == null
      ? []
      : (conversationState.messagesByConversation[activeConversationId] ?? []);
  const historyMeta =
    activeConversationId == null
      ? { hasMore: false, isLoadingOlder: false }
      : (conversationState.historyMetaByConversation[activeConversationId] ?? {
          hasMore: false,
          isLoadingOlder: false,
          nextCursor: null,
        });

  React.useEffect(() => {
    return dependencies.gatewayClient.subscribe((event) => {
      if (event.type === 'messageReceived') {
        dependencies.conversationStore.applyInbound(event.message);
        return;
      }
      if (event.type === 'forceLogout') {
        dependencies.sessionStore.handleForceLogout(event.reason);
        return;
      }
      if (event.type === 'disconnected') {
        dependencies.sessionStore.setReconnecting('WebSocket connection closed.');
      }
    });
  }, [dependencies]);

  async function handleLogin(input: LoginCredentials): Promise<void> {
    dependencies.sessionStore.startSignIn(input);

    try {
      const authSession = await dependencies.authService.login(input);
      dependencies.sessionStore.setAuthenticated(authSession);

      const wsTicket = await dependencies.authService.issueWsTicket(authSession);
      dependencies.sessionStore.setTicket(wsTicket);

      const connection = await dependencies.gatewayClient.connect(wsTicket, authSession);
      dependencies.sessionStore.setConnected(connection);

      const [conversations, loadedFriends, loadedIncomingRequests] = await Promise.all([
        dependencies.chatService.listConversations(authSession),
        dependencies.chatService.listFriends(authSession),
        dependencies.chatService.listIncomingFriendRequests(authSession),
      ]);
      dependencies.conversationStore.setConversations(conversations);
      setFriends(loadedFriends);
      setIncomingRequests(loadedIncomingRequests);

      const firstConversation = conversations[0];
      if (firstConversation != null) {
        await activateConversation(firstConversation.conversationId, authSession);
      }
    } catch (error) {
      dependencies.sessionStore.setError(error instanceof Error ? error.message : 'Unable to establish session.');
    }
  }

  async function activateConversation(
    conversationId: string,
    authSession = toAuthSession(session),
  ): Promise<void> {
    dependencies.conversationStore.setActiveConversation(conversationId);
    if (authSession == null) {
      return;
    }

    const existing = conversationState.messagesByConversation[conversationId] ?? [];
    if (existing.length > 0) {
      return;
    }

    const page = await dependencies.chatService.getHistory(conversationId, null, authSession);
    dependencies.conversationStore.replaceHistory(conversationId, page);
  }

  async function handleLoadOlder(): Promise<void> {
    if (activeConversationId == null) {
      return;
    }
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const meta = conversationState.historyMetaByConversation[activeConversationId];
    if (meta == null || !meta.hasMore || meta.nextCursor == null) {
      return;
    }

    dependencies.conversationStore.setLoadingOlder(activeConversationId, true);
    const page = await dependencies.chatService.getHistory(
      activeConversationId,
      meta.nextCursor,
      authSession,
    );
    dependencies.conversationStore.prependHistory(activeConversationId, page);
  }

  async function handleSend(text: string): Promise<void> {
    if (activeConversationId == null) {
      return;
    }
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const activeConversation = conversationState.conversations.find(
      (conversation) => conversation.conversationId === activeConversationId,
    );
    const recipientId = activeConversation?.peerUserId ?? derivePeerUserId(activeConversationId, authSession.profile.userId);
    if (recipientId == null) {
      dependencies.sessionStore.setError('Direct recipient unavailable.');
      return;
    }

    const optimistic: MessageItem = {
      localId: `local_${Date.now()}`,
      conversationId: activeConversationId,
      senderId: authSession.profile.userId,
      senderDisplay: authSession.profile.displayName,
      direction: 'outgoing',
      text,
      timestamp: Date.now(),
      status: 'sending',
    };

    dependencies.conversationStore.addOptimisticOutgoing(optimistic);

    try {
      const delivered = await dependencies.gatewayClient.sendText({
        conversationId: activeConversationId,
        recipientId,
        text,
        localId: optimistic.localId,
        session: authSession,
      });
      dependencies.conversationStore.markDelivered(
        activeConversationId,
        optimistic.localId,
        delivered.serverId,
        delivered.sentAt,
      );
    } catch (error) {
      dependencies.conversationStore.markFailed(
        activeConversationId,
        optimistic.localId,
        formatSendError(error),
      );
    }
  }

  async function handleSendFriendRequest(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    await dependencies.chatService.sendFriendRequest(friendUserId, authSession);
  }

  async function handleAcceptFriendRequest(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const friend = await dependencies.chatService.acceptFriendRequest(friendUserId, authSession);
    setIncomingRequests((current) => current.filter((item) => item.userId !== friend.userId));
    setFriends((current) => {
      if (current.some((item) => item.userId === friend.userId)) {
        return current;
      }
      return [friend, ...current];
    });
    await handleStartDirectConversation(friend.userId);
  }

  async function handleStartDirectConversation(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const conversation = await dependencies.chatService.startDirectConversation(friendUserId, authSession);
    dependencies.conversationStore.upsertConversation(conversation);
    await activateConversation(conversation.conversationId, authSession);
  }

  const keepChatShell =
    session.profile != null && (session.stage === 'connected' || session.lifecycle === 'reconnecting');

  if (!keepChatShell) {
    return (
      <LoginView
        stage={session.stage}
        statusLabel={session.statusLabel}
        environmentLabel={session.environmentLabel}
        errorMessage={session.errorMessage}
        onSubmit={handleLogin}
      />
    );
  }

  return (
    <ChatLayout
      session={session}
      conversations={conversationState.conversations}
      activeConversationId={activeConversationId}
      messages={messages}
      friends={friends}
      incomingRequests={incomingRequests}
      isLoadingOlder={historyMeta.isLoadingOlder}
      hasMore={historyMeta.hasMore}
      onSelectConversation={activateConversation}
      onSendFriendRequest={handleSendFriendRequest}
      onAcceptFriendRequest={handleAcceptFriendRequest}
      onStartDirectConversation={handleStartDirectConversation}
      onLoadOlder={handleLoadOlder}
      onSend={handleSend}
    />
  );
}

function derivePeerUserId(conversationId: string, currentUserId: string): string | null {
  if (!conversationId.startsWith('single:')) {
    return null;
  }
  const parts = conversationId.split(':');
  if (parts.length !== 3) {
    return null;
  }
  if (parts[1] === currentUserId) {
    return parts[2];
  }
  if (parts[2] === currentUserId) {
    return parts[1];
  }
  return null;
}

function toAuthSession(session: ReturnType<AppDependencies['sessionStore']['getState']>): AuthSession | null {
  if (
    session.profile == null ||
    session.sessionId == null ||
    session.deviceId == null ||
    session.accessToken == null ||
    session.refreshToken == null ||
    session.accessExpireAt == null ||
    session.refreshExpireAt == null
  ) {
    return null;
  }

  return {
    sessionId: session.sessionId,
    deviceId: session.deviceId,
    profile: session.profile,
    platform: session.platform,
    deviceName: session.deviceName,
    tokens: {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      accessExpireAt: session.accessExpireAt,
      refreshExpireAt: session.refreshExpireAt,
    },
  };
}
