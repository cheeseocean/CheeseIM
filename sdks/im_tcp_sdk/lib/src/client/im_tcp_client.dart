import 'dart:async';
import 'dart:convert';

import '../model/auth_session.dart';
import '../model/chat_message_item.dart';
import '../model/connection_snapshot.dart';
import '../model/message_delivery_status.dart';
import '../model/send_message_exception.dart';
import '../protocol/message_types.dart';
import 'im_tcp_session_manager.dart';

final class ImTcpClient {
  ImTcpClient({required this.sessionManager}) {
    _inboundSubscription = sessionManager.inboundMessages.listen((message) {
      final chatMessage = _decodeInboundMessage(message);
      if (chatMessage != null && !_inboundMessagesController.isClosed) {
        _inboundMessagesController.add(chatMessage);
      }
    });
  }

  final ImTcpSessionManager sessionManager;
  final StreamController<ChatMessageItem> _inboundMessagesController =
      StreamController<ChatMessageItem>.broadcast();
  late final StreamSubscription _inboundSubscription;

  ConnectionSnapshot get snapshot => sessionManager.snapshot;
  Stream<ConnectionSnapshot> get connectionSnapshots =>
      sessionManager.snapshots;
  Stream<ChatMessageItem> get inboundMessages =>
      _inboundMessagesController.stream;

  Future<void> connect(AuthSession session) {
    return sessionManager.connect(session);
  }

  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  }) async {
    final session = sessionManager.currentSession;
    if (session == null) {
      throw classifySendMessageException('IM session is not connected.');
    }

    final clientMsgId = 'client-${DateTime.now().microsecondsSinceEpoch}';
    late final dynamic response;
    try {
      response = await sessionManager.sendRequest(
        msgType: TcpMessageTypes.sendMsgReq,
        data: jsonEncode(<String, Object>{
          'clientMsgID': clientMsgId,
          'recvID': peerId,
          'content': text,
          'contentType': 101,
          'sessionType': 1,
        }),
      );
    } on Object catch (error) {
      throw classifySendMessageException(
        error.toString().replaceFirst(RegExp(r'^[^:]+: '), ''),
      );
    }

    final payload = jsonDecode(response.data ?? '{}') as Map<String, dynamic>;
    return ChatMessageItem(
      localId: 'local-$clientMsgId',
      clientMsgId: payload['clientMsgID'] as String? ?? clientMsgId,
      serverMsgId: payload['serverMsgID'] as String?,
      senderId: session.userId,
      receiverId: peerId,
      peerId: peerId,
      content: text,
      contentType: 101,
      sessionType: 1,
      sendTime: (payload['sendTime'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
      status: MessageDeliveryStatus.sent,
      isOutgoing: true,
    );
  }

  Future<void> dispose() async {
    await _inboundSubscription.cancel();
    await _inboundMessagesController.close();
    await sessionManager.dispose();
  }

  ChatMessageItem? _decodeInboundMessage(dynamic messageEnvelope) {
    final session = sessionManager.currentSession;
    final payload =
        jsonDecode(messageEnvelope.data ?? '{}') as Map<String, dynamic>;
    final senderId = payload['sendID'] as String? ?? '';
    final receiverId = payload['recvID'] as String? ?? '';
    final currentUserId = session?.userId ?? receiverId;
    final outgoing = senderId == currentUserId;
    final peerId = outgoing ? receiverId : senderId;
    if (peerId.isEmpty) {
      return null;
    }
    return ChatMessageItem(
      localId:
          'local-${payload['clientMsgID'] ?? payload['serverMsgID'] ?? DateTime.now().microsecondsSinceEpoch}',
      clientMsgId: payload['clientMsgID'] as String? ?? '',
      serverMsgId: payload['serverMsgID'] as String?,
      senderId: senderId,
      receiverId: receiverId,
      peerId: peerId,
      content: payload['content'] as String? ?? '',
      contentType: (payload['contentType'] as num?)?.toInt() ?? 101,
      sessionType: (payload['sessionType'] as num?)?.toInt() ?? 1,
      sendTime: (payload['sendTime'] as num?)?.toInt() ??
          DateTime.now().millisecondsSinceEpoch,
      status: outgoing
          ? MessageDeliveryStatus.sent
          : MessageDeliveryStatus.received,
      isOutgoing: outgoing,
    );
  }
}
