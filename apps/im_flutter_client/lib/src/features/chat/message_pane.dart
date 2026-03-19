import 'package:flutter/material.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

final class MessagePane extends StatefulWidget {
  const MessagePane({
    required this.peerId,
    required this.messages,
    required this.onSendText,
    required this.onRetry,
    this.onBack,
    super.key,
  });

  final String peerId;
  final List<ChatMessageItem> messages;
  final Future<void> Function(String text) onSendText;
  final Future<void> Function(ChatMessageItem message) onRetry;
  final VoidCallback? onBack;

  @override
  State<MessagePane> createState() => _MessagePaneState();
}

final class _MessagePaneState extends State<MessagePane> {
  late final TextEditingController _composerController;

  @override
  void initState() {
    super.initState();
    _composerController = TextEditingController();
  }

  @override
  void dispose() {
    _composerController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Material(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Material(
            color: Colors.grey.shade100,
            child: SafeArea(
              bottom: false,
              child: ListTile(
                leading: widget.onBack == null
                    ? null
                    : IconButton(
                        onPressed: widget.onBack,
                        icon: const Icon(Icons.arrow_back),
                      ),
                title: Text('Chat with user-${widget.peerId}'),
              ),
            ),
          ),
          Expanded(
            child: ListView.builder(
              itemCount: widget.messages.length,
              itemBuilder: (context, index) {
                final message = widget.messages[index];
                return ListTile(
                  title: Align(
                    alignment: message.isOutgoing
                        ? Alignment.centerRight
                        : Alignment.centerLeft,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 8),
                      decoration: BoxDecoration(
                        color: message.isOutgoing
                            ? Colors.blue.shade50
                            : Colors.grey.shade200,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(message.content),
                    ),
                  ),
                  subtitle: message.status == MessageDeliveryStatus.failed
                      ? Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            if (message.failureReason != null)
                              Text(message.failureReason!),
                            TextButton(
                              onPressed: () => widget.onRetry(message),
                              child: const Text('Retry'),
                            ),
                          ],
                        )
                      : Text(
                          switch (message.status) {
                            MessageDeliveryStatus.sending => 'Sending',
                            MessageDeliveryStatus.sent => 'Sent',
                            MessageDeliveryStatus.failed => 'Failed',
                            MessageDeliveryStatus.received => 'Received',
                          },
                        ),
                );
              },
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: <Widget>[
                  Expanded(
                    child: TextField(
                      controller: _composerController,
                      decoration:
                          const InputDecoration(hintText: 'Type a message'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: () async {
                      final text = _composerController.text.trim();
                      if (text.isEmpty) {
                        return;
                      }
                      _composerController.clear();
                      await widget.onSendText(text);
                    },
                    child: const Text('Send'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
