import type { ChatService } from './contracts';
import type { AuthSession, ConversationSummary, FriendRequestSummary, FriendSummary, HistoryPage, MessageItem } from '../domain/types';
import { fetchJson } from './http';

interface RealChatServiceOptions {
  authBaseUrl: string;
  imBaseUrl: string;
}

interface ConversationSummaryPayload {
  conversationId: string;
  title: string;
  subtitle: string;
  kind: 'DIRECT' | 'GROUP' | 'CHANNEL';
  peerUserId?: string;
  lastMessagePreview: string;
  lastMessageTime: number;
  unreadCount: number;
  accentColor: string;
}

interface FriendSummaryPayload {
  userId: string;
  displayName: string;
  avatarSeed: string;
}

interface FriendRequestPayload extends FriendSummaryPayload {
  status: 'PENDING';
}

interface HistoryMessagePayload {
  serverMsgId: string;
  clientMsgId: string;
  conversationId: string;
  senderId: string;
  receiverId: string;
  content: string;
  contentType: number;
  sequence: number;
  createdAt: string;
}

export function createRealChatService(options: RealChatServiceOptions): ChatService {
  return {
    async listConversations(session: AuthSession): Promise<ConversationSummary[]> {
      const response = await fetchJson<ConversationSummaryPayload[]>(
        `${options.imBaseUrl}/api/im/conversations?limit=20`,
        {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${session.tokens.accessToken}`,
        },
      },
      );

      return response.map((conversation) => ({
        conversationId: conversation.conversationId,
        title: conversation.title,
        subtitle: conversation.subtitle,
        kind: conversation.kind,
        peerUserId: conversation.peerUserId,
        lastMessagePreview: conversation.lastMessagePreview,
        lastMessageTime: conversation.lastMessageTime,
        unreadCount: conversation.unreadCount,
        accentColor: conversation.accentColor,
      }));
    },
    async listFriends(session: AuthSession): Promise<FriendSummary[]> {
      const response = await fetchJson<FriendSummaryPayload[]>(
        `${options.authBaseUrl}/api/im/friends`,
        {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
        },
      );
      return response.map((friend) => ({
        userId: friend.userId,
        displayName: friend.displayName,
        avatarSeed: friend.avatarSeed,
      }));
    },
    async listIncomingFriendRequests(session: AuthSession): Promise<FriendRequestSummary[]> {
      const response = await fetchJson<FriendRequestPayload[]>(
        `${options.authBaseUrl}/api/im/friends/requests`,
        {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
        },
      );
      return response.map((request) => ({
        userId: request.userId,
        displayName: request.displayName,
        avatarSeed: request.avatarSeed,
        status: request.status,
      }));
    },
    async sendFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendRequestSummary> {
      const response = await fetchJson<FriendRequestPayload>(
        `${options.authBaseUrl}/api/im/friends`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
          body: JSON.stringify({ friendUserId }),
        },
      );
      return {
        userId: response.userId,
        displayName: response.displayName,
        avatarSeed: response.avatarSeed,
        status: response.status,
      };
    },
    async acceptFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendSummary> {
      const response = await fetchJson<FriendSummaryPayload>(
        `${options.authBaseUrl}/api/im/friends/accept`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
          body: JSON.stringify({ friendUserId }),
        },
      );
      return {
        userId: response.userId,
        displayName: response.displayName,
        avatarSeed: response.avatarSeed,
      };
    },
    async startDirectConversation(friendUserId: string, session: AuthSession): Promise<ConversationSummary> {
      const response = await fetchJson<ConversationSummaryPayload>(
        `${options.imBaseUrl}/api/im/direct-conversations`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${session.tokens.accessToken}`,
          },
          body: JSON.stringify({ friendUserId }),
        },
      );
      return {
        conversationId: response.conversationId,
        title: response.title,
        subtitle: response.subtitle,
        kind: response.kind,
        peerUserId: response.peerUserId,
        lastMessagePreview: response.lastMessagePreview,
        lastMessageTime: response.lastMessageTime,
        unreadCount: response.unreadCount,
        accentColor: response.accentColor,
      };
    },
    async getHistory(
      conversationId: string,
      cursor: string | null,
      session: AuthSession,
    ): Promise<HistoryPage> {
      const limit = cursor == null ? 20 : 20;
      const response = await fetchJson<HistoryMessagePayload[]>(
        `${options.imBaseUrl}/api/im/conversations/${conversationId}/messages?limit=${limit}`,
        {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${session.tokens.accessToken}`,
        },
      },
      );

      return {
        items: response.map((message) => mapHistoryMessage(message, session)),
        nextCursor: null,
        hasMore: false,
      };
    },
  };
}

function mapHistoryMessage(payload: HistoryMessagePayload, session: AuthSession): MessageItem {
  const outgoing = payload.senderId === session.profile.userId;
  return {
    localId: payload.clientMsgId || payload.serverMsgId,
    serverId: payload.serverMsgId,
    conversationId: payload.conversationId,
    senderId: payload.senderId,
    senderDisplay: outgoing ? session.profile.displayName : payload.senderId,
    direction: outgoing ? 'outgoing' : 'incoming',
    text: payload.content,
    timestamp: Date.parse(payload.createdAt),
    status: outgoing ? 'delivered' : 'received',
  };
}
