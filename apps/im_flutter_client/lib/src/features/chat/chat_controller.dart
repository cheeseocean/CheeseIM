import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

import '../../core/bootstrap.dart';
import 'send_error_message.dart';

final class ChatController extends ChangeNotifier {
  ChatController(
    this._client, {
    List<ConversationSummary> initialConversations =
        const <ConversationSummary>[],
    List<ChatMessageItem> initialMessages = const <ChatMessageItem>[],
  }) : _connectionSnapshot = _client.snapshot {
    for (final summary in initialConversations) {
      _conversations[summary.conversationId] = summary;
    }
    for (final message in initialMessages) {
      _messagesByConversation
          .putIfAbsent(message.peerId, () => <ChatMessageItem>[])
          .add(message);
    }
    _connectionSubscription = _client.connectionSnapshots.listen((snapshot) {
      _connectionSnapshot = snapshot;
      notifyListeners();
    });
    _inboundSubscription = _client.inboundMessages.listen(_applyInboundMessage);
  }

  final ImClientGateway _client;
  final Map<String, ConversationSummary> _conversations =
      <String, ConversationSummary>{};
  final Map<String, List<ChatMessageItem>> _messagesByConversation =
      <String, List<ChatMessageItem>>{};
  late final StreamSubscription<ConnectionSnapshot> _connectionSubscription;
  late final StreamSubscription<ChatMessageItem> _inboundSubscription;

  ConnectionSnapshot _connectionSnapshot;
  String? _selectedConversationId;
  int _localCounter = 0;

  ConnectionSnapshot get connectionSnapshot => _connectionSnapshot;

  List<ConversationSummary> get conversations {
    final items = _conversations.values.toList();
    items.sort(
        (left, right) => right.lastMessageTime.compareTo(left.lastMessageTime));
    return items;
  }

  String? get selectedConversationId => _selectedConversationId;

  List<ChatMessageItem> get messages => selectedConversationId == null
      ? const <ChatMessageItem>[]
      : List<ChatMessageItem>.unmodifiable(
          _messagesByConversation[selectedConversationId] ??
              const <ChatMessageItem>[],
        );

  Future<void> sendText(String peerId, String text) async {
    final message = _buildOptimisticMessage(peerId: peerId, text: text);
    _upsertLocalMessage(message);
    openConversation(peerId);

    try {
      final sent = await _client.sendTextMessage(peerId: peerId, text: text);
      _replaceMessage(
        peerId,
        message.clientMsgId,
        sent.copyWith(
          status: MessageDeliveryStatus.sent,
          clearFailureReason: true,
        ),
      );
    } on Object catch (error) {
      _replaceMessage(
        peerId,
        message.clientMsgId,
        message.copyWith(
          status: MessageDeliveryStatus.failed,
          failureReason: _formatSendError(error),
        ),
      );
    }
  }

  Future<void> retry(ChatMessageItem message) async {
    final pending = _buildOptimisticMessage(
      peerId: message.peerId,
      text: message.content,
    );
    _replaceMessage(message.peerId, message.clientMsgId, pending);

    try {
      final sent = await _client.sendTextMessage(
        peerId: message.peerId,
        text: message.content,
      );
      _replaceMessage(
        message.peerId,
        pending.clientMsgId,
        sent.copyWith(
          status: MessageDeliveryStatus.sent,
          clearFailureReason: true,
        ),
      );
    } on Object catch (error) {
      _replaceMessage(
        message.peerId,
        pending.clientMsgId,
        pending.copyWith(
          status: MessageDeliveryStatus.failed,
          failureReason: _formatSendError(error),
        ),
      );
    }
  }

  void openConversation(String conversationId) {
    _selectedConversationId = conversationId;
    final current = _conversations[conversationId];
    if (current != null && current.unreadCount != 0) {
      _conversations[conversationId] = current.copyWith(unreadCount: 0);
    }
    notifyListeners();
  }

  void clearSelection() {
    _selectedConversationId = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _connectionSubscription.cancel();
    _inboundSubscription.cancel();
    super.dispose();
  }

  void _applyInboundMessage(ChatMessageItem message) {
    final currentUnread = _selectedConversationId == message.peerId
        ? 0
        : (_conversations[message.peerId]?.unreadCount ?? 0) + 1;
    _messagesByConversation
        .putIfAbsent(message.peerId, () => <ChatMessageItem>[])
        .add(message);
    _conversations[message.peerId] = ConversationSummary(
      conversationId: message.peerId,
      peerId: message.peerId,
      lastMessagePreview: message.content,
      lastMessageTime: message.sendTime,
      unreadCount: currentUnread,
    );
    notifyListeners();
  }

  void _upsertLocalMessage(ChatMessageItem message) {
    _messagesByConversation
        .putIfAbsent(message.peerId, () => <ChatMessageItem>[])
        .add(message);
    _conversations[message.peerId] = ConversationSummary(
      conversationId: message.peerId,
      peerId: message.peerId,
      lastMessagePreview: message.content,
      lastMessageTime: message.sendTime,
      unreadCount: _conversations[message.peerId]?.unreadCount ?? 0,
    );
    notifyListeners();
  }

  ChatMessageItem _buildOptimisticMessage({
    required String peerId,
    required String text,
  }) {
    final counter = ++_localCounter;
    return ChatMessageItem(
      localId: 'local-$counter',
      clientMsgId: 'client-${DateTime.now().microsecondsSinceEpoch}-$counter',
      senderId: 'self',
      receiverId: peerId,
      peerId: peerId,
      content: text,
      contentType: 1,
      sessionType: 1,
      sendTime: DateTime.now().millisecondsSinceEpoch,
      status: MessageDeliveryStatus.sending,
      isOutgoing: true,
    );
  }

  String _formatSendError(Object error) {
    return formatSendErrorMessage(error);
  }

  void _replaceMessage(
      String peerId, String clientMsgId, ChatMessageItem updated) {
    final messages = _messagesByConversation[peerId];
    if (messages == null) {
      return;
    }
    final index =
        messages.indexWhere((message) => message.clientMsgId == clientMsgId);
    if (index == -1) {
      return;
    }
    messages[index] = updated;
    _conversations[peerId] = ConversationSummary(
      conversationId: peerId,
      peerId: peerId,
      lastMessagePreview: updated.content,
      lastMessageTime: updated.sendTime,
      unreadCount: _conversations[peerId]?.unreadCount ?? 0,
    );
    notifyListeners();
  }
}
