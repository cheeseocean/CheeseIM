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
import { getUiCopy, type UiLocale, type UiTheme } from './ui';

export function App({ dependencies }: { dependencies?: AppDependencies }) {
  const stableDependencies = React.useMemo(
    () => dependencies ?? createDefaultDependencies(),
    [dependencies],
  );
  const [theme, setTheme] = React.useState<UiTheme>(() => readPreference('cheeseim.web.theme', 'light'));
  const [locale, setLocale] = React.useState<UiLocale>(() => readPreference('cheeseim.web.locale', 'en'));

  React.useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem('cheeseim.web.theme', theme);
  }, [theme]);

  React.useEffect(() => {
    document.documentElement.dataset.locale = locale;
    document.documentElement.lang = locale === 'zh' ? 'zh-CN' : 'en';
    window.localStorage.setItem('cheeseim.web.locale', locale);
  }, [locale]);

  return (
    <AppProviders dependencies={stableDependencies}>
      <AppBody
        dependencies={stableDependencies}
        theme={theme}
        locale={locale}
        onThemeChange={setTheme}
        onLocaleChange={setLocale}
      />
    </AppProviders>
  );
}

function AppBody({
  dependencies,
  theme,
  locale,
  onThemeChange,
  onLocaleChange,
}: {
  dependencies: AppDependencies;
  theme: UiTheme;
  locale: UiLocale;
  onThemeChange(theme: UiTheme): void;
  onLocaleChange(locale: UiLocale): void;
}) {
  const [friends, setFriends] = React.useState<FriendSummary[]>([]);
  const [incomingRequests, setIncomingRequests] = React.useState<FriendRequestSummary[]>([]);
  const [outgoingRequests, setOutgoingRequests] = React.useState<FriendRequestSummary[]>([]);
  const [typingByConversation, setTypingByConversation] = React.useState<Record<string, boolean>>({});
  const friendRefreshInFlightRef = React.useRef(false);
  const friendRefreshQueuedRef = React.useRef(false);
  const typingTimeoutsRef = React.useRef<Record<string, number>>({});
  const lastTypingAtRef = React.useRef<Record<string, number>>({});
  const sentReadCursorIdsRef = React.useRef<Record<string, number>>({});
  const copy = React.useMemo(() => getUiCopy(locale), [locale]);
  const session = React.useSyncExternalStore(
    dependencies.sessionStore.subscribe,
    dependencies.sessionStore.getState,
  );
  const conversationState = React.useSyncExternalStore(
    dependencies.conversationStore.subscribe,
    dependencies.conversationStore.getState,
  );
  const restoreAttemptRef = React.useRef<string | null>(null);

  const activeConversationId = conversationState.activeConversationId;
  const messages =
    activeConversationId == null
      ? []
      : (conversationState.messagesByConversation[activeConversationId] ?? []);
  const typingDisplay = activeConversationId != null && typingByConversation[activeConversationId]
    ? copy.chat.typing
    : null;
  const historyMeta =
    activeConversationId == null
      ? { hasMore: false, isLoadingOlder: false }
      : (conversationState.historyMetaByConversation[activeConversationId] ?? {
          hasMore: false,
          isLoadingOlder: false,
          nextCursor: null,
        });

  async function refreshFriendState(authSession = toAuthSession(session)): Promise<void> {
    if (authSession == null) {
      return;
    }
    if (friendRefreshInFlightRef.current) {
      friendRefreshQueuedRef.current = true;
      return;
    }

    friendRefreshInFlightRef.current = true;
    try {
      do {
        friendRefreshQueuedRef.current = false;
        const [loadedFriends, loadedIncomingRequests, loadedOutgoingRequests] = await Promise.all([
          dependencies.chatService.listFriends(authSession),
          dependencies.chatService.listIncomingFriendRequests(authSession),
          dependencies.chatService.listOutgoingFriendRequests(authSession),
        ]);
        setFriends(loadedFriends);
        setIncomingRequests(loadedIncomingRequests);
        setOutgoingRequests(loadedOutgoingRequests);
      } while (friendRefreshQueuedRef.current);
    } finally {
      friendRefreshInFlightRef.current = false;
    }
  }

  React.useEffect(() => {
    return dependencies.gatewayClient.subscribe((event) => {
      if (event.type === 'messageReceived') {
        dependencies.conversationStore.applyInbound(event.message);
        if (
          event.message.direction === 'incoming' &&
          activeConversationId === event.message.conversationId &&
          isPageVisible()
        ) {
          void sendReadCursor(event.message.conversationId);
        }
        return;
      }
      if (event.type === 'typing') {
        if (event.senderId === session.profile?.userId) {
          return;
        }
        setTypingByConversation((current) => ({
          ...current,
          [event.conversationId]: true,
        }));
        const previous = typingTimeoutsRef.current[event.conversationId];
        if (previous != null) {
          window.clearTimeout(previous);
        }
        typingTimeoutsRef.current[event.conversationId] = window.setTimeout(() => {
          setTypingByConversation((current) => ({
            ...current,
            [event.conversationId]: false,
          }));
        }, 3000);
        return;
      }
      if (event.type === 'read') {
        if (event.clientMsgId !== '' && sentReadCursorIdsRef.current[event.clientMsgId] != null) {
          delete sentReadCursorIdsRef.current[event.clientMsgId];
          return;
        }
        if (event.senderId === (session.profile?.userId ?? '')) {
          return;
        }
        const normalizedConversationId = normalizeConversationIdForState(
          event.conversationId,
          dependencies.conversationStore.getState().conversations,
          session.profile?.userId ?? '',
        );
        const hasRemoteIdentity =
          event.senderId.trim() !== '' || event.recipientId.trim() !== '' || isDirectConversationId(event.conversationId);
        if (!hasRemoteIdentity || normalizedConversationId == null || event.seq <= 0) {
          return;
        }
        dependencies.conversationStore.markRead(normalizedConversationId, event.seq);
        return;
      }
      if (event.type === 'revoke') {
        dependencies.conversationStore.markRecalled(event.conversationId, event.serverId);
        return;
      }
      if (event.type === 'forceLogout') {
        dependencies.sessionStore.handleForceLogout(event.reason);
        return;
      }
      if (event.type === 'friendStateChanged') {
        void refreshFriendState();
        return;
      }
      if (event.type === 'disconnected') {
        dependencies.sessionStore.setReconnecting('WebSocket connection closed.');
      }
    });
  }, [activeConversationId, dependencies, session]);

  React.useEffect(() => {
    const authSession = dependencies.sessionStore.getRestorableSession();
    if (authSession == null) {
      restoreAttemptRef.current = null;
      return;
    }
    if (session.stage === 'connected' || session.lifecycle === 'connecting') {
      restoreAttemptRef.current = authSession.sessionId;
      return;
    }
    if (restoreAttemptRef.current === authSession.sessionId) {
      return;
    }
    restoreAttemptRef.current = authSession.sessionId;
    void restoreSession(authSession);
  }, [dependencies, session.stage, session.lifecycle, session.sessionId]);

  async function restoreSession(authSession: AuthSession): Promise<void> {
    try {
      const wsTicket = await dependencies.authService.issueWsTicket(authSession);
      dependencies.sessionStore.setTicket(wsTicket);

      const connection = await dependencies.gatewayClient.connect(wsTicket, authSession);
      dependencies.sessionStore.setConnected(connection);

      const conversations = await dependencies.chatService.listConversations(authSession);
      await refreshFriendState(authSession);
      dependencies.conversationStore.setConversations(conversations);

      const latestState = dependencies.conversationStore.getState();
      const targetConversationId =
        latestState.activeConversationId ?? conversations[0]?.conversationId ?? null;
      if (targetConversationId != null) {
        await activateConversation(targetConversationId, authSession, false);
      }
    } catch (error) {
      restoreAttemptRef.current = null;
      dependencies.sessionStore.setError(
        error instanceof Error ? error.message : 'Unable to restore session.',
      );
    }
  }

  async function handleLogin(input: LoginCredentials): Promise<void> {
    dependencies.sessionStore.startSignIn(input);

    try {
      const authSession = await dependencies.authService.login(input);
      dependencies.sessionStore.setAuthenticated(authSession);
      restoreAttemptRef.current = authSession.sessionId;
      await restoreSession(authSession);
    } catch (error) {
      restoreAttemptRef.current = null;
      dependencies.sessionStore.setError(error instanceof Error ? error.message : 'Unable to establish session.');
    }
  }

  async function activateConversation(
    conversationId: string,
    authSession = toAuthSession(session),
    confirmRead = false,
  ): Promise<void> {
    dependencies.conversationStore.setActiveConversation(conversationId);
    if (authSession == null) {
      return;
    }

    const existing =
      dependencies.conversationStore.getState().messagesByConversation[conversationId] ?? [];
    if (existing.length > 0) {
      if (confirmRead && isPageVisible()) {
        await sendReadCursor(conversationId, authSession);
      }
      return;
    }

    const page = await dependencies.chatService.getHistory(conversationId, null, authSession);
    dependencies.conversationStore.replaceHistory(conversationId, page);
    if (confirmRead && isPageVisible()) {
      await sendReadCursor(conversationId, authSession);
    }
  }

  async function handleLoadOlder(): Promise<void> {
    if (activeConversationId == null) {
      return;
    }
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const latestMeta =
      dependencies.conversationStore.getState().historyMetaByConversation[activeConversationId];
    if (latestMeta == null || !latestMeta.hasMore || latestMeta.nextCursor == null) {
      return;
    }

    dependencies.conversationStore.setLoadingOlder(activeConversationId, true);
    const page = await dependencies.chatService.getHistory(
      activeConversationId,
      latestMeta.nextCursor,
      authSession,
    );
    dependencies.conversationStore.prependHistory(activeConversationId, page);
  }

  function handleClearMessages(): void {
    if (activeConversationId == null) {
      return;
    }
    dependencies.conversationStore.clearConversationMessages(activeConversationId);
  }

  function handleTyping(text: string): void {
    if (activeConversationId == null || text.trim() === '') {
      return;
    }
    const now = Date.now();
    if ((lastTypingAtRef.current[activeConversationId] ?? 0) + 1500 > now) {
      return;
    }
    lastTypingAtRef.current[activeConversationId] = now;
    void sendSignal(activeConversationId, 'typing', 'typing');
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

  async function handleRecallMessage(message: MessageItem): Promise<void> {
    if (message.serverId == null) {
      return;
    }
    await sendSignal(message.conversationId, 'revoke', message.serverId);
    dependencies.conversationStore.markRecalled(message.conversationId, message.serverId);
  }

  async function handleSendFriendRequest(friendUserId: string, requestMessage: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const request = await dependencies.chatService.sendFriendRequest(friendUserId, requestMessage, authSession);
    setOutgoingRequests((current) => {
      const next = current.filter((item) => item.userId !== request.userId);
      return [request, ...next];
    });
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
    setOutgoingRequests((current) => current.filter((item) => item.userId !== friend.userId));
    await handleStartDirectConversation(friend.userId);
  }

  async function handleRejectFriendRequest(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    await dependencies.chatService.rejectFriendRequest(friendUserId, authSession);
    setIncomingRequests((current) => current.filter((item) => item.userId !== friendUserId));
  }

  async function handleCancelFriendRequest(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    await dependencies.chatService.cancelFriendRequest(friendUserId, authSession);
    setOutgoingRequests((current) => current.filter((item) => item.userId !== friendUserId));
  }

  async function handleStartDirectConversation(friendUserId: string): Promise<void> {
    const authSession = toAuthSession(session);
    if (authSession == null) {
      return;
    }
    const conversation = await dependencies.chatService.startDirectConversation(friendUserId, authSession);
    dependencies.conversationStore.upsertConversation(conversation);
    await activateConversation(conversation.conversationId, authSession, true);
  }

  async function sendReadCursor(
    conversationId: string,
    authSession = toAuthSession(session),
  ): Promise<void> {
    if (authSession == null) {
      return;
    }
    const latestSeq = latestIncomingSeq(
      conversationId,
      dependencies.conversationStore.getState().messagesByConversation[conversationId] ?? [],
    );
    if (latestSeq == null) {
      return;
    }
    const recipientId = resolveRecipient(conversationId, authSession.profile.userId);
    if (recipientId == null) {
      return;
    }
    pruneSentReadCursorIds(sentReadCursorIdsRef.current);
    const localId = `read_${Date.now()}`;
    sentReadCursorIdsRef.current[localId] = Date.now();
    await dependencies.gatewayClient.sendMessage({
      conversationId,
      recipientId,
      localId,
      content: String(latestSeq),
      contentType: 2004,
      session: authSession,
    });
  }

  async function sendSignal(
    conversationId: string,
    type: 'typing' | 'revoke',
    content: string,
    authSession = toAuthSession(session),
  ): Promise<void> {
    if (authSession == null) {
      return;
    }
    const recipientId = resolveRecipient(conversationId, authSession.profile.userId);
    if (recipientId == null) {
      return;
    }
    await dependencies.gatewayClient.sendMessage({
      conversationId,
      recipientId,
      localId: `${type}_${Date.now()}`,
      content,
      contentType: type === 'typing' ? 4002 : 2005,
      session: authSession,
    });
  }

  function resolveRecipient(conversationId: string, currentUserId: string): string | null {
    const conversation = dependencies.conversationStore.getState().conversations.find(
      (candidate) => candidate.conversationId === conversationId,
    );
    return conversation?.peerUserId ?? derivePeerUserId(conversationId, currentUserId);
  }

  const keepChatShell =
    session.profile != null && (session.stage === 'connected' || session.lifecycle === 'reconnecting');

  if (!keepChatShell) {
    return (
      <LoginView
        copy={copy}
        theme={theme}
        locale={locale}
        onThemeChange={onThemeChange}
        onLocaleChange={onLocaleChange}
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
      copy={copy}
      theme={theme}
      locale={locale}
      onThemeChange={onThemeChange}
      onLocaleChange={onLocaleChange}
      onSignOut={() => dependencies.sessionStore.reset()}
      session={session}
      conversations={conversationState.conversations}
      activeConversationId={activeConversationId}
      messages={messages}
      typingDisplay={typingDisplay}
      friends={friends}
      incomingRequests={incomingRequests}
      outgoingRequests={outgoingRequests}
      isLoadingOlder={historyMeta.isLoadingOlder}
      hasMore={historyMeta.hasMore}
      onSelectConversation={(conversationId) => activateConversation(conversationId, undefined, true)}
      onSendFriendRequest={handleSendFriendRequest}
      onAcceptFriendRequest={handleAcceptFriendRequest}
      onRejectFriendRequest={handleRejectFriendRequest}
      onCancelFriendRequest={handleCancelFriendRequest}
      onStartDirectConversation={handleStartDirectConversation}
      onLoadOlder={handleLoadOlder}
      onClearMessages={handleClearMessages}
      onTyping={handleTyping}
      onRecallMessage={handleRecallMessage}
      onSend={handleSend}
    />
  );
}

function readPreference<T extends string>(key: string, fallback: T): T {
  if (typeof window === 'undefined') {
    return fallback;
  }
  return (window.localStorage.getItem(key) as T | null) ?? fallback;
}

function isPageVisible(): boolean {
  if (typeof document === 'undefined') {
    return true;
  }
  return document.visibilityState !== 'hidden';
}

function derivePeerUserId(conversationId: string, currentUserId: string): string | null {
  if (!conversationId.startsWith('single:') && !conversationId.startsWith('c1:')) {
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

function isDirectConversationId(conversationId: string): boolean {
  return conversationId.startsWith('single:') || conversationId.startsWith('c1:');
}

function normalizeConversationIdForState(
  conversationId: string,
  conversations: Array<{ conversationId: string; peerUserId?: string }>,
  currentUserId: string,
): string | null {
  if (conversations.some((conversation) => conversation.conversationId === conversationId)) {
    return conversationId;
  }
  const peerUserId = derivePeerUserId(conversationId, currentUserId);
  if (peerUserId == null) {
    return null;
  }
  return (
    conversations.find((conversation) => conversation.peerUserId === peerUserId)?.conversationId ??
    conversationId
  );
}

function latestIncomingSeq(conversationId: string, messages: MessageItem[]): number | null {
  const seqs = messages
    .filter((message) => message.conversationId === conversationId && message.direction === 'incoming' && message.seq != null)
    .map((message) => message.seq as number);
  if (seqs.length === 0) {
    return null;
  }
  return Math.max(...seqs);
}

function pruneSentReadCursorIds(ids: Record<string, number>): void {
  const cutoff = Date.now() - 60_000;
  Object.keys(ids).forEach((id) => {
    if (ids[id] < cutoff) {
      delete ids[id];
    }
  });
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
