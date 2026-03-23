import type {
  AuthSession,
  ConversationSummary,
  FriendSummary,
  FriendRequestSummary,
  GatewayConnection,
  HistoryPage,
  LoginCredentials,
  MessageItem,
  WsTicket,
} from '../domain/types';

export interface AuthService {
  login(input: LoginCredentials): Promise<AuthSession>;
  issueWsTicket(session: AuthSession): Promise<WsTicket>;
}

export interface ChatService {
  listConversations(session: AuthSession): Promise<ConversationSummary[]>;
  listFriends(session: AuthSession): Promise<FriendSummary[]>;
  listIncomingFriendRequests(session: AuthSession): Promise<FriendRequestSummary[]>;
  listOutgoingFriendRequests(session: AuthSession): Promise<FriendRequestSummary[]>;
  sendFriendRequest(friendUserId: string, requestMessage: string, session: AuthSession): Promise<FriendRequestSummary>;
  acceptFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendSummary>;
  rejectFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendRequestSummary>;
  cancelFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendRequestSummary>;
  startDirectConversation(friendUserId: string, session: AuthSession): Promise<ConversationSummary>;
  getHistory(
    conversationId: string,
    cursor: string | null,
    session: AuthSession,
  ): Promise<HistoryPage>;
}

export interface SendTextRequest {
  conversationId: string;
  recipientId: string;
  text: string;
  localId: string;
  session: AuthSession;
}

export interface SendMessageRequest {
  conversationId: string;
  recipientId: string;
  localId: string;
  content: string;
  contentType: number;
  session: AuthSession;
  attachedInfo?: string;
}

export interface SendTextResult {
  serverId: string;
  sentAt: number;
}

export type GatewayEvent =
  | { type: 'messageReceived'; message: MessageItem }
  | { type: 'typing'; conversationId: string; senderId: string }
  | { type: 'read'; conversationId: string; seq: number; senderId: string; recipientId: string; clientMsgId: string }
  | { type: 'revoke'; conversationId: string; serverId: string }
  | { type: 'friendStateChanged' }
  | { type: 'forceLogout'; reason: string }
  | { type: 'disconnected' };

export interface GatewayClient {
  connect(ticket: WsTicket, session: AuthSession): Promise<GatewayConnection>;
  sendText(input: SendTextRequest): Promise<SendTextResult>;
  sendMessage(input: SendMessageRequest): Promise<SendTextResult>;
  subscribe(listener: (event: GatewayEvent) => void): () => void;
}
