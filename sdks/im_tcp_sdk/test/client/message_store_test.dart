import 'package:im_tcp_sdk/src/client/message_store.dart';
import 'package:im_tcp_sdk/src/model/chat_message_item.dart';
import 'package:im_tcp_sdk/src/model/message_delivery_status.dart';
import 'package:test/test.dart';

void main() {
  test('aggregates conversation preview and avoids duplicate inbound messages',
      () {
    final store = MessageStore();

    final inbound = ChatMessageItem(
      localId: 'local-1',
      clientMsgId: 'c1',
      serverMsgId: 's1',
      senderId: 'u2',
      receiverId: 'u1',
      peerId: 'u2',
      content: 'hi',
      contentType: 101,
      sessionType: 1,
      sendTime: 1710000000000,
      status: MessageDeliveryStatus.received,
      isOutgoing: false,
    );

    store.applyInbound(inbound);
    store.applyInbound(inbound);

    expect(store.conversations, hasLength(1));
    expect(store.conversations.single.unreadCount, 1);
    expect(store.messagesForConversation('u2'), hasLength(1));
    expect(store.conversations.single.lastMessagePreview, 'hi');
  });

  test('marks optimistic outbound message as sent when ack arrives', () {
    final store = MessageStore();
    final outgoing = ChatMessageItem(
      localId: 'local-out',
      clientMsgId: 'c-out',
      senderId: 'u1',
      receiverId: 'u2',
      peerId: 'u2',
      content: 'hello',
      contentType: 101,
      sessionType: 1,
      sendTime: 1710000000000,
      status: MessageDeliveryStatus.sending,
      isOutgoing: true,
    );

    store.addOptimisticOutgoing(outgoing);
    final updated = store.markSent('c-out', 's-out', 1710000000001);

    expect(updated?.serverMsgId, 's-out');
    expect(updated?.status, MessageDeliveryStatus.sent);
    expect(store.conversations.single.lastMessagePreview, 'hello');
  });
}
