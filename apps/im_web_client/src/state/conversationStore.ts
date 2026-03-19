import type { ConversationSummary, HistoryPage, MessageItem } from '../domain/types';

interface ConversationHistoryMeta {
  nextCursor: string | null;
  hasMore: boolean;
  isLoadingOlder: boolean;
}

interface ConversationState {
  conversations: ConversationSummary[];
  activeConversationId: string | null;
  messagesByConversation: Record<string, MessageItem[]>;
  historyMetaByConversation: Record<string, ConversationHistoryMeta>;
}

export interface ConversationStore {
  getState(): ConversationState;
  subscribe(listener: () => void): () => void;
  setConversations(conversations: ConversationSummary[]): void;
  upsertConversation(conversation: ConversationSummary): void;
  setActiveConversation(conversationId: string | null): void;
  replaceHistory(conversationId: string, page: HistoryPage): void;
  prependHistory(conversationId: string, page: HistoryPage): void;
  setLoadingOlder(conversationId: string, isLoading: boolean): void;
  addOptimisticOutgoing(message: MessageItem): void;
  applyInbound(message: MessageItem): void;
  markDelivered(conversationId: string, localId: string, serverId: string, sentAt: number): void;
  markFailed(conversationId: string, localId: string, reason: string): void;
}

const initialState: ConversationState = {
  conversations: [],
  activeConversationId: null,
  messagesByConversation: {},
  historyMetaByConversation: {},
};

export function createConversationStore(): ConversationStore {
  let state = initialState;
  const listeners = new Set<() => void>();

  function emit(): void {
    listeners.forEach((listener) => listener());
  }

  function updateConversations(
    conversationId: string,
    updater: (current: ConversationSummary) => ConversationSummary,
  ): void {
    state = {
      ...state,
      conversations: state.conversations.map((conversation) =>
        conversation.conversationId === conversationId ? updater(conversation) : conversation,
      ),
    };
  }

  return {
    getState() {
      return state;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    setConversations(conversations) {
      state = {
        ...state,
        conversations: [...conversations].sort((left, right) => right.lastMessageTime - left.lastMessageTime),
      };
      emit();
    },
    upsertConversation(conversation) {
      const existing = state.conversations.find((item) => item.conversationId === conversation.conversationId);
      state = {
        ...state,
        conversations: existing == null
          ? [conversation, ...state.conversations].sort((left, right) => right.lastMessageTime - left.lastMessageTime)
          : state.conversations
              .map((item) => item.conversationId === conversation.conversationId ? { ...item, ...conversation } : item)
              .sort((left, right) => right.lastMessageTime - left.lastMessageTime),
      };
      emit();
    },
    setActiveConversation(conversationId) {
      state = {
        ...state,
        activeConversationId: conversationId,
        conversations:
          conversationId == null
            ? state.conversations
            : state.conversations.map((conversation) =>
                conversation.conversationId === conversationId
                  ? { ...conversation, unreadCount: 0 }
                  : conversation,
              ),
      };
      emit();
    },
    replaceHistory(conversationId, page) {
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: [...page.items],
        },
        historyMetaByConversation: {
          ...state.historyMetaByConversation,
          [conversationId]: {
            nextCursor: page.nextCursor,
            hasMore: page.hasMore,
            isLoadingOlder: false,
          },
        },
      };
      emit();
    },
    prependHistory(conversationId, page) {
      const current = state.messagesByConversation[conversationId] ?? [];
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: [...page.items, ...current],
        },
        historyMetaByConversation: {
          ...state.historyMetaByConversation,
          [conversationId]: {
            nextCursor: page.nextCursor,
            hasMore: page.hasMore,
            isLoadingOlder: false,
          },
        },
      };
      emit();
    },
    setLoadingOlder(conversationId, isLoading) {
      const current = state.historyMetaByConversation[conversationId] ?? {
        nextCursor: null,
        hasMore: false,
        isLoadingOlder: false,
      };
      state = {
        ...state,
        historyMetaByConversation: {
          ...state.historyMetaByConversation,
          [conversationId]: {
            ...current,
            isLoadingOlder: isLoading,
          },
        },
      };
      emit();
    },
    addOptimisticOutgoing(message) {
      const current = state.messagesByConversation[message.conversationId] ?? [];
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [message.conversationId]: [...current, message],
        },
      };
      updateConversations(message.conversationId, (conversation) => ({
        ...conversation,
        lastMessagePreview: message.text,
        lastMessageTime: message.timestamp,
      }));
      emit();
    },
    applyInbound(message) {
      const current = state.messagesByConversation[message.conversationId] ?? [];
      if (current.some((item) => item.serverId != null && item.serverId === message.serverId)) {
        return;
      }
      const existingConversation = state.conversations.find(
        (conversation) => conversation.conversationId === message.conversationId,
      );
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [message.conversationId]: [...current, message],
        },
        conversations:
          existingConversation == null
            ? [
                {
                  conversationId: message.conversationId,
                  title: message.senderDisplay,
                  subtitle: 'Direct message',
                  kind: 'DIRECT',
                  lastMessagePreview: message.text,
                  lastMessageTime: message.timestamp,
                  unreadCount: state.activeConversationId === message.conversationId ? 0 : 1,
                  accentColor: '#79d7ff',
                },
                ...state.conversations,
              ]
            : state.conversations,
      };
      if (existingConversation != null) {
        updateConversations(message.conversationId, (conversation) => ({
          ...conversation,
          lastMessagePreview: message.text,
          lastMessageTime: message.timestamp,
          unreadCount:
            state.activeConversationId === message.conversationId
              ? 0
              : conversation.unreadCount + 1,
        }));
      }
      emit();
    },
    markDelivered(conversationId, localId, serverId, sentAt) {
      const nextMessages = (state.messagesByConversation[conversationId] ?? []).map((message) =>
        message.localId === localId
          ? {
              ...message,
              serverId,
              timestamp: sentAt,
              status: 'delivered' as const,
              failureReason: undefined,
            }
          : message,
      );
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: nextMessages,
        },
      };
      updateConversations(conversationId, (conversation) => ({
        ...conversation,
        lastMessagePreview: nextMessages.at(-1)?.text ?? conversation.lastMessagePreview,
        lastMessageTime: sentAt,
      }));
      emit();
    },
    markFailed(conversationId, localId, reason) {
      state = {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: (state.messagesByConversation[conversationId] ?? []).map((message) =>
            message.localId === localId
              ? {
                  ...message,
                  status: 'failed' as const,
                  failureReason: reason,
                }
              : message,
          ),
        },
      };
      emit();
    },
  };
}
