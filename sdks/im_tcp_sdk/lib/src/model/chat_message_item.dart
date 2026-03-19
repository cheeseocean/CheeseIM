import 'message_delivery_status.dart';

final class ChatMessageItem {
  const ChatMessageItem({
    required this.localId,
    required this.clientMsgId,
    this.serverMsgId,
    required this.senderId,
    required this.receiverId,
    required this.peerId,
    required this.content,
    required this.contentType,
    required this.sessionType,
    required this.sendTime,
    required this.status,
    this.failureReason,
    required this.isOutgoing,
  });

  final String localId;
  final String clientMsgId;
  final String? serverMsgId;
  final String senderId;
  final String receiverId;
  final String peerId;
  final String content;
  final int contentType;
  final int sessionType;
  final int sendTime;
  final MessageDeliveryStatus status;
  final String? failureReason;
  final bool isOutgoing;

  ChatMessageItem copyWith({
    String? serverMsgId,
    int? sendTime,
    MessageDeliveryStatus? status,
    String? failureReason,
    bool clearFailureReason = false,
  }) {
    return ChatMessageItem(
      localId: localId,
      clientMsgId: clientMsgId,
      serverMsgId: serverMsgId ?? this.serverMsgId,
      senderId: senderId,
      receiverId: receiverId,
      peerId: peerId,
      content: content,
      contentType: contentType,
      sessionType: sessionType,
      sendTime: sendTime ?? this.sendTime,
      status: status ?? this.status,
      failureReason:
          clearFailureReason ? null : failureReason ?? this.failureReason,
      isOutgoing: isOutgoing,
    );
  }
}
