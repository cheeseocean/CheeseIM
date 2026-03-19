import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_flutter_client/src/core/bootstrap.dart';
import 'package:im_flutter_client/src/features/chat/chat_controller.dart';
import 'package:im_flutter_client/src/features/chat/chat_shell.dart';
import 'package:im_flutter_client/src/features/chat/conversation_list_pane.dart';
import 'package:im_flutter_client/src/features/chat/message_pane.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

void main() {
  testWidgets('uses split pane on wide screens', (tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(_buildChatShell());

    expect(find.byType(ConversationListPane), findsOneWidget);
    expect(find.byType(MessagePane), findsOneWidget);
  });

  testWidgets('uses stacked navigation on narrow screens', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(_buildChatShell());

    expect(find.byType(ConversationListPane), findsOneWidget);

    await tester.tap(find.text('user-u2'));
    await tester.pumpAndSettle();

    expect(find.byType(MessagePane), findsOneWidget);
  });

  testWidgets('shows failure reason for failed outgoing messages', (tester) async {
    tester.view.physicalSize = const Size(1440, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ChatShell(
            controller: ChatController(
              _FakeChatClient(),
              initialConversations: const <ConversationSummary>[
                ConversationSummary(
                  conversationId: 'u2',
                  peerId: 'u2',
                  lastMessagePreview: 'hello',
                  lastMessageTime: 1710000000000,
                  unreadCount: 0,
                ),
              ],
              initialMessages: const <ChatMessageItem>[
                ChatMessageItem(
                  localId: 'local-1',
                  clientMsgId: 'client-1',
                  senderId: 'u1',
                  receiverId: 'u2',
                  peerId: 'u2',
                  content: 'hello',
                  contentType: 1,
                  sessionType: 1,
                  sendTime: 1710000000000,
                  status: MessageDeliveryStatus.failed,
                  failureReason: 'Permission denied',
                  isOutgoing: true,
                ),
              ],
            )..openConversation('u2'),
          ),
        ),
      ),
    );

    expect(find.text('Permission denied'), findsOneWidget);
  });
}

Widget _buildChatShell() {
  return MaterialApp(
    home: Scaffold(
      body: ChatShell(
        controller: ChatController(
          _FakeChatClient(),
          initialConversations: const <ConversationSummary>[
            ConversationSummary(
              conversationId: 'u2',
              peerId: 'u2',
              lastMessagePreview: 'hello',
              lastMessageTime: 1710000000000,
              unreadCount: 1,
            ),
          ],
          initialMessages: const <ChatMessageItem>[
            ChatMessageItem(
              localId: 'local-1',
              clientMsgId: 'client-1',
              senderId: 'u1',
              receiverId: 'u2',
              peerId: 'u2',
              content: 'hello',
              contentType: 1,
              sessionType: 1,
              sendTime: 1710000000000,
              status: MessageDeliveryStatus.sent,
              isOutgoing: true,
            ),
          ],
        ),
      ),
    ),
  );
}

final class _FakeChatClient implements ImClientGateway {
  @override
  ConnectionSnapshot get snapshot =>
      const ConnectionSnapshot(state: ConnectionLifecycle.ready);

  @override
  Stream<ConnectionSnapshot> get connectionSnapshots =>
      const Stream<ConnectionSnapshot>.empty();

  @override
  Stream<ChatMessageItem> get inboundMessages =>
      const Stream<ChatMessageItem>.empty();

  @override
  Future<void> connect(AuthSession session) async {}

  @override
  Future<void> dispose() async {}

  @override
  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  }) {
    throw UnimplementedError();
  }
}
