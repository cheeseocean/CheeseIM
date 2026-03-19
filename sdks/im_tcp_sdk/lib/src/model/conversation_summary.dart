final class ConversationSummary {
  const ConversationSummary({
    required this.conversationId,
    required this.peerId,
    required this.lastMessagePreview,
    required this.lastMessageTime,
    required this.unreadCount,
  });

  final String conversationId;
  final String peerId;
  final String lastMessagePreview;
  final int lastMessageTime;
  final int unreadCount;

  ConversationSummary copyWith({
    String? lastMessagePreview,
    int? lastMessageTime,
    int? unreadCount,
  }) {
    return ConversationSummary(
      conversationId: conversationId,
      peerId: peerId,
      lastMessagePreview: lastMessagePreview ?? this.lastMessagePreview,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      unreadCount: unreadCount ?? this.unreadCount,
    );
  }
}
