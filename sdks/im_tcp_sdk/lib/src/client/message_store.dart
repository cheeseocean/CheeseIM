import '../model/chat_message_item.dart';
import '../model/conversation_summary.dart';
import '../model/message_delivery_status.dart';

final class MessageStore {
  final Map<String, List<ChatMessageItem>> _messagesByConversation =
      <String, List<ChatMessageItem>>{};
  final Map<String, ConversationSummary> _conversations =
      <String, ConversationSummary>{};
  final Set<String> _seenServerIds = <String>{};
  final Set<String> _seenClientIds = <String>{};

  List<ConversationSummary> get conversations {
    final values = _conversations.values.toList();
    values.sort(
        (left, right) => right.lastMessageTime.compareTo(left.lastMessageTime));
    return values;
  }

  List<ChatMessageItem> messagesForConversation(String conversationId) =>
      List<ChatMessageItem>.unmodifiable(
          _messagesByConversation[conversationId] ?? const <ChatMessageItem>[]);

  void addOptimisticOutgoing(ChatMessageItem message) {
    _seenClientIds.add(message.clientMsgId);
    _append(message);
    _upsertConversation(message, incrementUnread: false);
  }

  void applyInbound(ChatMessageItem message) {
    if (message.serverMsgId != null &&
        !_seenServerIds.add(message.serverMsgId!)) {
      return;
    }
    if (!_seenClientIds.add(message.clientMsgId)) {
      return;
    }
    _append(message);
    _upsertConversation(message, incrementUnread: true);
  }

  ChatMessageItem? markSent(
      String clientMsgId, String serverMsgId, int sendTime) {
    for (final entry in _messagesByConversation.entries) {
      final index = entry.value
          .indexWhere((message) => message.clientMsgId == clientMsgId);
      if (index == -1) {
        continue;
      }

      final updated = entry.value[index].copyWith(
        serverMsgId: serverMsgId,
        sendTime: sendTime,
        status: MessageDeliveryStatus.sent,
      );
      entry.value[index] = updated;
      _seenServerIds.add(serverMsgId);
      _upsertConversation(updated, incrementUnread: false);
      return updated;
    }
    return null;
  }

  void _append(ChatMessageItem message) {
    final messages = _messagesByConversation.putIfAbsent(
        message.peerId, () => <ChatMessageItem>[]);
    messages.add(message);
  }

  void _upsertConversation(ChatMessageItem message,
      {required bool incrementUnread}) {
    final current = _conversations[message.peerId];
    _conversations[message.peerId] = (current ??
            ConversationSummary(
              conversationId: message.peerId,
              peerId: message.peerId,
              lastMessagePreview: message.content,
              lastMessageTime: message.sendTime,
              unreadCount: 0,
            ))
        .copyWith(
      lastMessagePreview: message.content,
      lastMessageTime: message.sendTime,
      unreadCount: incrementUnread
          ? (current?.unreadCount ?? 0) + 1
          : current?.unreadCount ?? 0,
    );
  }
}
