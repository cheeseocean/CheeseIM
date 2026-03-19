import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:im_flutter_client/src/app.dart';
import 'package:im_flutter_client/src/core/bootstrap.dart';
import 'package:im_flutter_client/src/features/auth/login_screen.dart';
import 'package:im_flutter_client/src/features/chat/connection_banner.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

void main() {
  testWidgets('shows reconnecting banner', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ConnectionBanner(
            snapshot: ConnectionSnapshot(
              state: ConnectionLifecycle.reconnecting,
              reconnectAttempt: 1,
            ),
          ),
        ),
      ),
    );

    expect(find.text('Reconnecting...'), findsOneWidget);
  });

  testWidgets('returns to login when auth is rejected', (tester) async {
    final client = _FakeClientGateway(
      initialSnapshot: const ConnectionSnapshot(
        state: ConnectionLifecycle.closed,
        lastError: 'Authentication failed',
        errorKind: ClientErrorKind.authRejected,
      ),
    );

    await tester.pumpWidget(
      ImFlutterApp(
        dependencies: AppDependencies(client: client),
      ),
    );

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('Authentication failed'), findsOneWidget);
  });

  testWidgets('keeps chat shell visible when network closes after ready',
      (tester) async {
    final client = _FakeClientGateway(
      initialSnapshot: const ConnectionSnapshot(
        state: ConnectionLifecycle.ready,
      ),
    );

    await tester.pumpWidget(
      ImFlutterApp(
        dependencies: AppDependencies(client: client),
      ),
    );

    client.emit(
      const ConnectionSnapshot(
        state: ConnectionLifecycle.closed,
        lastError: 'socket closed',
        errorKind: ClientErrorKind.networkInterrupted,
      ),
    );
    await tester.pump();

    expect(find.text('Conversations'), findsOneWidget);
    expect(find.byType(LoginScreen), findsNothing);
  });

  testWidgets('returns to login when force logout is received', (tester) async {
    final client = _FakeClientGateway(
      initialSnapshot: const ConnectionSnapshot(
        state: ConnectionLifecycle.closed,
        lastError: 'Logged out on another device',
        errorKind: ClientErrorKind.forceLogout,
      ),
    );

    await tester.pumpWidget(
      ImFlutterApp(
        dependencies: AppDependencies(client: client),
      ),
    );

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('Logged out on another device'), findsOneWidget);
  });
}

final class _FakeClientGateway implements ImClientGateway {
  _FakeClientGateway({required ConnectionSnapshot initialSnapshot})
      : _snapshot = initialSnapshot;

  final StreamController<ConnectionSnapshot> _snapshots =
      StreamController<ConnectionSnapshot>.broadcast();
  ConnectionSnapshot _snapshot;

  @override
  ConnectionSnapshot get snapshot => _snapshot;

  @override
  Stream<ConnectionSnapshot> get connectionSnapshots => _snapshots.stream;

  @override
  Stream<ChatMessageItem> get inboundMessages =>
      const Stream<ChatMessageItem>.empty();

  @override
  Future<void> connect(AuthSession session) async {}

  @override
  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<void> dispose() async {
    await _snapshots.close();
  }

  void emit(ConnectionSnapshot snapshot) {
    _snapshot = snapshot;
    _snapshots.add(snapshot);
  }
}
