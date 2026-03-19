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
  sendFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendRequestSummary>;
  acceptFriendRequest(friendUserId: string, session: AuthSession): Promise<FriendSummary>;
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

export interface SendTextResult {
  serverId: string;
  sentAt: number;
}

export type GatewayEvent =
  | { type: 'messageReceived'; message: MessageItem }
  | { type: 'forceLogout'; reason: string }
  | { type: 'disconnected' };

export interface GatewayClient {
  connect(ticket: WsTicket, session: AuthSession): Promise<GatewayConnection>;
  sendText(input: SendTextRequest): Promise<SendTextResult>;
  subscribe(listener: (event: GatewayEvent) => void): () => void;
}
