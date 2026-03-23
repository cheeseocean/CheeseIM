import { describe, expect, it } from 'vitest';

import { createConversationStore } from '../state/conversationStore';

describe('conversationStore', () => {
  it('prepends older history and preserves newer messages for the active conversation', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'conv-design-ops',
        title: 'Design Ops',
        subtitle: 'Editorial channel',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 1,
        accentColor: '#6ef1c6',
      },
    ]);
    store.replaceHistory('conv-design-ops', {
      items: [
        {
          localId: 'msg-2',
          serverId: 'msg-2',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          direction: 'incoming',
          text: 'Welcome to the relay desk.',
          timestamp: 200,
          status: 'received',
        },
      ],
      nextCursor: 'cursor-older',
      hasMore: true,
    });

    store.prependHistory('conv-design-ops', {
      items: [
        {
          localId: 'msg-1',
          serverId: 'msg-1',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          direction: 'incoming',
          text: "Yesterday's handoff is attached to the brief.",
          timestamp: 100,
          status: 'received',
        },
      ],
      nextCursor: null,
      hasMore: false,
    });

    expect(store.getState().messagesByConversation['conv-design-ops'].map((item) => item.localId)).toEqual([
      'msg-1',
      'msg-2',
    ]);
    expect(store.getState().historyMetaByConversation['conv-design-ops']).toMatchObject({
      nextCursor: null,
      hasMore: false,
      isLoadingOlder: false,
    });
  });

  it('moves an optimistic outgoing message from sending to delivered', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'conv-design-ops',
        title: 'Design Ops',
        subtitle: 'Editorial channel',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 0,
        accentColor: '#6ef1c6',
      },
    ]);

    store.addOptimisticOutgoing({
      localId: 'local-1',
      conversationId: 'conv-design-ops',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Need the final mockups before noon.',
      timestamp: 300,
      status: 'sending',
    });

    store.markDelivered('conv-design-ops', 'local-1', 'msg-3', 400);

    expect(store.getState().messagesByConversation['conv-design-ops'][0]).toMatchObject({
      localId: 'local-1',
      serverId: 'msg-3',
      status: 'delivered',
      timestamp: 400,
    });
    expect(store.getState().conversations[0]).toMatchObject({
      lastMessagePreview: 'Need the final mockups before noon.',
      lastMessageTime: 400,
    });
  });

  it('merges inbound echo with an optimistic outgoing message that shares the same local id', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'conv-design-ops',
        title: 'Design Ops',
        subtitle: 'Editorial channel',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 0,
        accentColor: '#6ef1c6',
      },
    ]);

    store.addOptimisticOutgoing({
      localId: 'local-1',
      conversationId: 'conv-design-ops',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Need the final mockups before noon.',
      timestamp: 300,
      status: 'sending',
    });

    store.applyInbound({
      localId: 'local-1',
      serverId: 'msg-3',
      conversationId: 'conv-design-ops',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Need the final mockups before noon.',
      timestamp: 400,
      status: 'delivered',
    });

    expect(store.getState().messagesByConversation['conv-design-ops']).toHaveLength(1);
    expect(store.getState().messagesByConversation['conv-design-ops'][0]).toMatchObject({
      localId: 'local-1',
      serverId: 'msg-3',
      status: 'delivered',
      timestamp: 400,
    });
  });

  it('collapses the optimistic message when recv notify arrives before send ack', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'conv-design-ops',
        title: 'Design Ops',
        subtitle: 'Editorial channel',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 0,
        accentColor: '#6ef1c6',
      },
    ]);

    store.addOptimisticOutgoing({
      localId: 'local-1',
      conversationId: 'conv-design-ops',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Need the final mockups before noon.',
      timestamp: 300,
      status: 'sending',
    });

    store.applyInbound({
      localId: 'remote-shadow',
      serverId: 'msg-3',
      conversationId: 'conv-design-ops',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Need the final mockups before noon.',
      timestamp: 400,
      status: 'delivered',
    });

    store.markDelivered('conv-design-ops', 'local-1', 'msg-3', 400);

    expect(store.getState().messagesByConversation['conv-design-ops']).toHaveLength(1);
    expect(store.getState().messagesByConversation['conv-design-ops'][0]).toMatchObject({
      localId: 'local-1',
      serverId: 'msg-3',
      status: 'delivered',
      timestamp: 400,
    });
  });

  it('preserves read status when the outbound recv notify arrives after the read receipt', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'c1:u_design:u_operator',
        title: 'Mina Park',
        subtitle: 'Direct message',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 0,
        accentColor: '#79d7ff',
      },
    ]);

    store.addOptimisticOutgoing({
      localId: 'local-1',
      conversationId: 'c1:u_design:u_operator',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Please confirm receipt.',
      timestamp: 300,
      status: 'sending',
    });

    store.markDelivered('c1:u_design:u_operator', 'local-1', 'msg-3', 350);
    store.markRead('c1:u_design:u_operator', 21);

    store.applyInbound({
      localId: 'local-1',
      serverId: 'msg-3',
      seq: 21,
      conversationId: 'c1:u_design:u_operator',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Please confirm receipt.',
      timestamp: 360,
      status: 'delivered',
    });

    expect(store.getState().messagesByConversation['c1:u_design:u_operator'][0]).toMatchObject({
      localId: 'local-1',
      serverId: 'msg-3',
      status: 'read',
    });
  });

  it('does not downgrade a read message back to delivered when the send ack arrives later', () => {
    const store = createConversationStore();

    store.setConversations([
      {
        conversationId: 'c1:u_design:u_operator',
        title: 'Mina Park',
        subtitle: 'Direct message',
        kind: 'DIRECT',
        lastMessagePreview: 'Welcome to the relay desk.',
        lastMessageTime: 200,
        unreadCount: 0,
        accentColor: '#79d7ff',
      },
    ]);

    store.addOptimisticOutgoing({
      localId: 'local-1',
      conversationId: 'c1:u_design:u_operator',
      senderId: 'u_operator',
      senderDisplay: 'Avery Stone',
      direction: 'outgoing',
      text: 'Please confirm receipt.',
      timestamp: 300,
      status: 'sending',
    });

    store.markRead('c1:u_design:u_operator', 21);
    store.markDelivered('c1:u_design:u_operator', 'local-1', 'msg-3', 350);

    expect(store.getState().messagesByConversation['c1:u_design:u_operator'][0]).toMatchObject({
      localId: 'local-1',
      serverId: 'msg-3',
      status: 'read',
    });
  });

  it('clears only the targeted conversation messages', () => {
    const store = createConversationStore();

    store.replaceHistory('conv-design-ops', {
      items: [
        {
          localId: 'msg-1',
          serverId: 'msg-1',
          conversationId: 'conv-design-ops',
          senderId: 'u_design',
          senderDisplay: 'Mina Park',
          direction: 'incoming',
          text: 'older',
          timestamp: 100,
          status: 'received',
        },
      ],
      nextCursor: null,
      hasMore: false,
    });
    store.replaceHistory('conv-release-watch', {
      items: [
        {
          localId: 'msg-2',
          serverId: 'msg-2',
          conversationId: 'conv-release-watch',
          senderId: 'u_ops',
          senderDisplay: 'Theo Vale',
          direction: 'incoming',
          text: 'keep me',
          timestamp: 200,
          status: 'received',
        },
      ],
      nextCursor: null,
      hasMore: false,
    });

    store.clearConversationMessages('conv-design-ops');

    expect(store.getState().messagesByConversation['conv-design-ops']).toEqual([]);
    expect(store.getState().messagesByConversation['conv-release-watch']).toHaveLength(1);
  });
});
