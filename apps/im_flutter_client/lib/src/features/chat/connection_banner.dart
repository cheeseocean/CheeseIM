import 'package:flutter/material.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

final class ConnectionBanner extends StatelessWidget {
  const ConnectionBanner({required this.snapshot, super.key});

  final ConnectionSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final message = switch (snapshot.state) {
      ConnectionLifecycle.connecting => 'Connecting...',
      ConnectionLifecycle.authenticating => 'Authenticating...',
      ConnectionLifecycle.reconnecting => 'Reconnecting...',
      ConnectionLifecycle.closed when snapshot.lastError != null =>
        snapshot.lastError!,
      _ => null,
    };

    if (message == null) {
      return const SizedBox.shrink();
    }

    return Material(
      color: Colors.amber.shade100,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        child: Row(
          children: <Widget>[
            const Icon(Icons.wifi_tethering_error_rounded, size: 18),
            const SizedBox(width: 8),
            Text(message),
          ],
        ),
      ),
    );
  }
}
