import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:im_flutter_client/src/core/bootstrap.dart';
import 'package:im_flutter_client/src/features/chat/chat_controller.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

void main() {
  test('marks a failed message and retries it', () async {
    final client = _FakeChatClient();
    final controller = ChatController(client);

    unawaited(controller.sendText('u2', 'hello'));
    await Future<void>.delayed(Duration.zero);
    client.failPendingSend();
    await Future<void>.delayed(Duration.zero);

    expect(controller.messages.single.status, MessageDeliveryStatus.failed);
    expect(
      controller.messages.single.failureReason,
      'The server is temporarily unavailable.',
    );

    await controller.retry(controller.messages.single);

    expect(client.sendCallCount, 2);
    expect(controller.messages, hasLength(1));
    expect(
      controller.messages.single.status,
      MessageDeliveryStatus.sent,
    );
    expect(controller.messages.single.failureReason, isNull);
  });
}

final class _FakeChatClient implements ImClientGateway {
  final StreamController<ConnectionSnapshot> _snapshots =
      StreamController<ConnectionSnapshot>.broadcast();
  int sendCallCount = 0;
  Completer<ChatMessageItem>? _pendingSend;

  @override
  ConnectionSnapshot get snapshot =>
      const ConnectionSnapshot(state: ConnectionLifecycle.ready);

  @override
  Stream<ConnectionSnapshot> get connectionSnapshots => _snapshots.stream;

  @override
  Stream<ChatMessageItem> get inboundMessages =>
      const Stream<ChatMessageItem>.empty();

  @override
  Future<void> connect(AuthSession session) async {}

  @override
  Future<void> dispose() async {
    await _snapshots.close();
  }

  @override
  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  }) {
    sendCallCount += 1;
    if (sendCallCount > 1) {
      return Future<ChatMessageItem>.value(
        ChatMessageItem(
          localId: 'ack-1',
          clientMsgId: 'server-client-1',
          serverMsgId: 'server-1',
          senderId: 'self',
          receiverId: peerId,
          peerId: peerId,
          content: text,
          contentType: 1,
          sessionType: 1,
          sendTime: 1710000000000,
          status: MessageDeliveryStatus.sent,
          isOutgoing: true,
        ),
      );
    }
    final completer = Completer<ChatMessageItem>();
    _pendingSend = completer;
    return completer.future;
  }

  void failPendingSend() {
    _pendingSend?.completeError(StateError('internal server error'));
    _pendingSend = null;
  }
}
