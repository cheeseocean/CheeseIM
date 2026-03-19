import type { AuthSession, ConversationSummary, FriendRequestSummary, FriendSummary, HistoryPage, MessageItem, Platform, UserProfile } from '../domain/types';

const currentUser: UserProfile = {
  userId: 'u_operator',
  displayName: 'Avery Stone',
  title: 'Relay Operator',
  tenantName: 'Cheese Ocean Studio',
  avatarSeed: 'AS',
};

const conversationList: ConversationSummary[] = [
  {
    conversationId: 'conv-design-ops',
    title: 'Design Ops',
    subtitle: 'Editorial channel · Product handoff',
    kind: 'DIRECT',
    peerUserId: 'u_design',
    lastMessagePreview: 'Welcome to the relay desk.',
    lastMessageTime: Date.now() - 1000 * 60 * 7,
    unreadCount: 2,
    accentColor: '#6ef1c6',
  },
  {
    conversationId: 'conv-release-watch',
    title: 'Release Watch',
    subtitle: 'Operations channel · Incident window',
    kind: 'DIRECT',
    peerUserId: 'u_ops',
    lastMessagePreview: 'QA sign-off is still pending.',
    lastMessageTime: Date.now() - 1000 * 60 * 23,
    unreadCount: 0,
    accentColor: '#8aa8ff',
  },
];

const friendList: FriendSummary[] = [
  { userId: 'u_design', displayName: 'Mina Park', avatarSeed: 'MP' },
  { userId: 'u_ops', displayName: 'Theo Vale', avatarSeed: 'TV' },
];

const incomingFriendRequests: FriendRequestSummary[] = [
  { userId: 'u_editor', displayName: 'Rae Mercer', avatarSeed: 'RM', status: 'PENDING' },
];

const historyMap: Record<string, { initial: HistoryPage; older?: HistoryPage }> = {
  'conv-design-ops': {
    initial: {
      items: [
        inboundMessage({
          id: 'msg-1002',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          text: 'Welcome to the relay desk.',
          timestamp: Date.now() - 1000 * 60 * 20,
        }),
        outboundMessage({
          id: 'msg-1003',
          conversationId: 'conv-design-ops',
          text: 'I have the review queue up and the web client open.',
          timestamp: Date.now() - 1000 * 60 * 18,
        }),
      ],
      nextCursor: 'older-design-ops',
      hasMore: true,
    },
    older: {
      items: [
        inboundMessage({
          id: 'msg-1001',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          text: "Yesterday's handoff is attached to the brief.",
          timestamp: Date.now() - 1000 * 60 * 90,
        }),
      ],
      nextCursor: null,
      hasMore: false,
    },
  },
  'conv-release-watch': {
    initial: {
      items: [
        inboundMessage({
          id: 'msg-2001',
          conversationId: 'conv-release-watch',
          senderId: 'u_ops',
          senderDisplay: 'Theo Vale',
          text: 'QA sign-off is still pending.',
          timestamp: Date.now() - 1000 * 60 * 24,
        }),
      ],
      nextCursor: null,
      hasMore: false,
    },
  },
};

export function buildSession(input: {
  account: string;
  deviceName: string;
  platform: Platform;
}): AuthSession {
  const now = Date.now();

  return {
    sessionId: createId('sess'),
    deviceId: createId('dev'),
    profile: {
      ...currentUser,
      displayName: input.account === '' ? currentUser.displayName : currentUser.displayName,
    },
    platform: input.platform,
    deviceName: input.deviceName,
    tokens: {
      accessToken: createToken('atk'),
      refreshToken: createToken('rtk'),
      accessExpireAt: now + 30 * 60 * 1000,
      refreshExpireAt: now + 14 * 24 * 60 * 60 * 1000,
    },
  };
}

export function getConversationList(): ConversationSummary[] {
  return conversationList.map((conversation) => ({ ...conversation }));
}

export function getFriendList(): FriendSummary[] {
  return friendList.map((friend) => ({ ...friend }));
}

export function getIncomingFriendRequests(): FriendRequestSummary[] {
  return incomingFriendRequests.map((request) => ({ ...request }));
}

export function sendFriendRequest(userId: string): FriendRequestSummary {
  const existing = incomingFriendRequests.find((request) => request.userId === userId);
  if (existing != null) {
    return { ...existing };
  }
  return {
    userId,
    displayName: deriveDisplayName(userId),
    avatarSeed: deriveAvatarSeed(userId),
    status: 'PENDING',
  };
}

export function acceptFriendRequest(userId: string): FriendSummary {
  const requestIndex = incomingFriendRequests.findIndex((request) => request.userId === userId);
  if (requestIndex >= 0) {
    incomingFriendRequests.splice(requestIndex, 1);
  }
  const existing = friendList.find((friend) => friend.userId === userId);
  if (existing != null) {
    return { ...existing };
  }
  const friend = {
    userId,
    displayName: deriveDisplayName(userId),
    avatarSeed: deriveAvatarSeed(userId),
  };
  friendList.unshift(friend);
  return { ...friend };
}

export function startDirectConversation(friendUserId: string): ConversationSummary {
  const existing = conversationList.find((conversation) => conversation.peerUserId === friendUserId);
  if (existing != null) {
    return { ...existing };
  }
  const conversation: ConversationSummary = {
    conversationId: directConversationId(currentUser.userId, friendUserId),
    title: deriveDisplayName(friendUserId),
    subtitle: 'Direct conversation',
    kind: 'DIRECT',
    peerUserId: friendUserId,
    lastMessagePreview: 'No messages yet',
    lastMessageTime: Date.now(),
    unreadCount: 0,
    accentColor: pickAccentColor(friendUserId),
  };
  conversationList.unshift(conversation);
  historyMap[conversation.conversationId] = {
    initial: {
      items: [],
      nextCursor: null,
      hasMore: false,
    },
  };
  return { ...conversation };
}

export function getHistoryPage(conversationId: string, cursor: string | null): HistoryPage {
  const source = historyMap[conversationId];
  if (source == null) {
    return {
      items: [],
      nextCursor: null,
      hasMore: false,
    };
  }
  if (cursor == null) {
    return clonePage(source.initial);
  }
  if (cursor === 'older-design-ops' && source.older != null) {
    return clonePage(source.older);
  }
  return {
    items: [],
    nextCursor: null,
    hasMore: false,
  };
}

function clonePage(page: HistoryPage): HistoryPage {
  return {
    items: page.items.map((item) => ({ ...item })),
    nextCursor: page.nextCursor,
    hasMore: page.hasMore,
  };
}

function inboundMessage(input: {
  id: string;
  conversationId: string;
  senderId: string;
  senderDisplay: string;
  text: string;
  timestamp: number;
}): MessageItem {
  return {
    localId: input.id,
    serverId: input.id,
    conversationId: input.conversationId,
    senderId: input.senderId,
    senderDisplay: input.senderDisplay,
    direction: 'incoming',
    text: input.text,
    timestamp: input.timestamp,
    status: 'received',
  };
}

function outboundMessage(input: {
  id: string;
  conversationId: string;
  text: string;
  timestamp: number;
}): MessageItem {
  return {
    localId: input.id,
    serverId: input.id,
    conversationId: input.conversationId,
    senderId: currentUser.userId,
    senderDisplay: currentUser.displayName,
    direction: 'outgoing',
    text: input.text,
    timestamp: input.timestamp,
    status: 'delivered',
  };
}

function createId(prefix: string): string {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`;
}

function createToken(prefix: string): string {
  return `${prefix}.${Math.random().toString(36).slice(2, 12)}.${Math.random().toString(36).slice(2, 12)}`;
}

function directConversationId(userA: string, userB: string): string {
  return userA.localeCompare(userB) <= 0 ? `single:${userA}:${userB}` : `single:${userB}:${userA}`;
}

function deriveDisplayName(userId: string): string {
  return userId
    .replace(/@.*$/, '')
    .split(/[._-]/g)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ') || userId;
}

function deriveAvatarSeed(userId: string): string {
  const seed = userId
    .replace(/@.*$/, '')
    .split(/[._-]/g)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
  return seed || 'IM';
}

function pickAccentColor(seed: string): string {
  const palette = ['#6ef1c6', '#79d7ff', '#f8b56a', '#ff8f7a', '#99a8ff', '#8ce0b8'];
  return palette[Math.abs(hashCode(seed)) % palette.length];
}

function hashCode(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash << 5) - hash + value.charCodeAt(index);
    hash |= 0;
  }
  return hash;
}
