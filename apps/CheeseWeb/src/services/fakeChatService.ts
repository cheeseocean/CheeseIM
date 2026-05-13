import type { ChatService } from './contracts';
import type { AuthSession, ConversationSummary, FriendRequestSummary, FriendSummary, HistoryPage } from '../domain/types';
import {
  acceptFriendRequest,
  cancelFriendRequest,
  getConversationList,
  getFriendList,
  getHistoryPage,
  getIncomingFriendRequests,
  getOutgoingFriendRequests,
  rejectFriendRequest,
  sendFriendRequest,
  startDirectConversation,
} from './fakeData';

export function createFakeChatService(): ChatService {
  return {
    async listConversations(_session: AuthSession): Promise<ConversationSummary[]> {
      await sleep(120);
      return getConversationList();
    },
    async listFriends(_session: AuthSession): Promise<FriendSummary[]> {
      await sleep(80);
      return getFriendList();
    },
    async listIncomingFriendRequests(_session: AuthSession): Promise<FriendRequestSummary[]> {
      await sleep(80);
      return getIncomingFriendRequests();
    },
    async listOutgoingFriendRequests(_session: AuthSession): Promise<FriendRequestSummary[]> {
      await sleep(80);
      return getOutgoingFriendRequests();
    },
    async sendFriendRequest(friendUserId: string, requestMessage: string, _session: AuthSession): Promise<FriendRequestSummary> {
      await sleep(120);
      return sendFriendRequest(friendUserId, requestMessage);
    },
    async acceptFriendRequest(friendUserId: string, _session: AuthSession): Promise<FriendSummary> {
      await sleep(120);
      return acceptFriendRequest(friendUserId);
    },
    async rejectFriendRequest(friendUserId: string, _session: AuthSession): Promise<FriendRequestSummary> {
      await sleep(120);
      return rejectFriendRequest(friendUserId);
    },
    async cancelFriendRequest(friendUserId: string, _session: AuthSession): Promise<FriendRequestSummary> {
      await sleep(120);
      return cancelFriendRequest(friendUserId);
    },
    async startDirectConversation(friendUserId: string, _session: AuthSession): Promise<ConversationSummary> {
      await sleep(120);
      return startDirectConversation(friendUserId);
    },
    async getHistory(
      conversationId: string,
      cursor: string | null,
      _session: AuthSession,
    ): Promise<HistoryPage> {
      await sleep(cursor == null ? 120 : 220);
      return getHistoryPage(conversationId, cursor);
    },
  };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
