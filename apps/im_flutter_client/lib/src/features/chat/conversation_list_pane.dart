import 'package:flutter/material.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

final class ConversationListPane extends StatelessWidget {
  const ConversationListPane({
    required this.conversations,
    required this.selectedConversationId,
    required this.onSelectConversation,
    super.key,
  });

  final List<ConversationSummary> conversations;
  final String? selectedConversationId;
  final ValueChanged<String> onSelectConversation;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      child: ListView.builder(
        itemCount: conversations.length,
        itemBuilder: (context, index) {
          final summary = conversations[index];
          return ListTile(
            selected: summary.conversationId == selectedConversationId,
            title: Text('user-${summary.peerId}'),
            subtitle: Text(summary.lastMessagePreview),
            trailing: summary.unreadCount > 0
                ? CircleAvatar(
                    radius: 10,
                    child: Text('${summary.unreadCount}'),
                  )
                : null,
            onTap: () => onSelectConversation(summary.conversationId),
          );
        },
      ),
    );
  }
}
