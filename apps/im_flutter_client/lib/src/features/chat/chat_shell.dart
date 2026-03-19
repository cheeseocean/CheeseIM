import 'package:flutter/material.dart';

import 'chat_controller.dart';
import 'conversation_list_pane.dart';
import 'message_pane.dart';

final class ChatShell extends StatelessWidget {
  const ChatShell({required this.controller, super.key});

  final ChatController controller;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 720;
            if (wide) {
              return Row(
                children: <Widget>[
                  SizedBox(
                    width: 320,
                    child: ConversationListPane(
                      conversations: controller.conversations,
                      selectedConversationId: controller.selectedConversationId,
                      onSelectConversation: controller.openConversation,
                    ),
                  ),
                  const VerticalDivider(width: 1),
                  Expanded(
                    child: _buildMessagePane(controller, mobile: false),
                  ),
                ],
              );
            }

            if (controller.selectedConversationId == null) {
              return ConversationListPane(
                conversations: controller.conversations,
                selectedConversationId: controller.selectedConversationId,
                onSelectConversation: controller.openConversation,
              );
            }

            return _buildMessagePane(controller, mobile: true);
          },
        );
      },
    );
  }

  Widget _buildMessagePane(ChatController controller, {required bool mobile}) {
    final peerId = controller.selectedConversationId ??
        (controller.conversations.isNotEmpty
            ? controller.conversations.first.conversationId
            : null);
    if (peerId == null) {
      return const Center(child: Text('Select a conversation'));
    }
    return MessagePane(
      peerId: peerId,
      messages: controller.messages,
      onSendText: (text) => controller.sendText(peerId, text),
      onRetry: controller.retry,
      onBack: mobile ? controller.clearSelection : null,
    );
  }
}
