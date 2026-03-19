export type Platform = 'ios' | 'android' | 'web' | 'pc';

export type SessionStage =
  | 'signed_out'
  | 'signing_in'
  | 'issuing_ticket'
  | 'connecting'
  | 'connected'
  | 'error';

export type ConnectionLifecycle = 'offline' | 'connecting' | 'connected' | 'reconnecting';

export interface LoginCredentials {
  account: string;
  password: string;
  deviceName: string;
  platform: Platform;
}

export interface UserProfile {
  userId: string;
  displayName: string;
  title: string;
  tenantName: string;
  avatarSeed: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  accessExpireAt: number;
  refreshExpireAt: number;
}

export interface AuthSession {
  sessionId: string;
  deviceId: string;
  profile: UserProfile;
  platform: Platform;
  deviceName: string;
  tokens: AuthTokens;
}

export interface FriendSummary {
  userId: string;
  displayName: string;
  avatarSeed: string;
}

export interface FriendRequestSummary {
  userId: string;
  displayName: string;
  avatarSeed: string;
  status: 'PENDING';
}

export interface WsTicket {
  ticket: string;
  wsUrl: string;
  expireAt: number;
}

export interface GatewayConnection {
  connId: string;
  lifecycle: ConnectionLifecycle;
  transportLabel: string;
}

export interface SessionState {
  stage: SessionStage;
  lifecycle: ConnectionLifecycle;
  profile: UserProfile | null;
  sessionId: string | null;
  deviceId: string | null;
  deviceName: string;
  platform: Platform;
  accessToken: string | null;
  refreshToken: string | null;
  accessExpireAt: number | null;
  refreshExpireAt: number | null;
  wsTicket: string | null;
  wsTicketExpireAt: number | null;
  wsUrl: string | null;
  connId: string | null;
  statusLabel: string;
  ticketStatusLabel: string;
  transportLabel: string;
  environmentLabel: string;
  errorMessage?: string;
}

export type ConversationKind = 'DIRECT' | 'GROUP';

export interface ConversationSummary {
  conversationId: string;
  title: string;
  subtitle: string;
  kind: ConversationKind;
  peerUserId?: string;
  lastMessagePreview: string;
  lastMessageTime: number;
  unreadCount: number;
  accentColor: string;
}

export type MessageStatus = 'sending' | 'delivered' | 'failed' | 'received';

export interface MessageItem {
  localId: string;
  serverId?: string;
  conversationId: string;
  senderId: string;
  senderDisplay: string;
  direction: 'incoming' | 'outgoing';
  text: string;
  timestamp: number;
  status: MessageStatus;
  failureReason?: string;
}

export interface HistoryPage {
  items: MessageItem[];
  nextCursor: string | null;
  hasMore: boolean;
}
