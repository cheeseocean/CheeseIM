import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:im_flutter_client/src/core/bootstrap.dart';
import 'package:im_flutter_client/src/features/auth/login_controller.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

void main() {
  test('submits manual session parameters into the SDK', () async {
    final client = _FakeClientGateway();
    final controller = LoginController(client);

    await controller.login(
      host: '127.0.0.1',
      port: 5148,
      userId: 'u1',
      token: 'jwt',
      platformId: 2,
    );

    expect(client.lastSession?.userId, 'u1');
    expect(client.lastSession?.host, '127.0.0.1');
    expect(client.lastSession?.port, 5148);
  });

  test('captures connection failures for login UI', () async {
    final client =
        _FakeClientGateway(connectError: StateError('socket closed'));
    final controller = LoginController(client);

    await controller.login(
      host: '127.0.0.1',
      port: 5148,
      userId: 'u1',
      token: 'jwt',
      platformId: 2,
    );

    expect(controller.errorMessage, 'socket closed');
  });
}

final class _FakeClientGateway implements ImClientGateway {
  _FakeClientGateway({this.connectError});

  final Object? connectError;
  final StreamController<ConnectionSnapshot> _snapshots =
      StreamController<ConnectionSnapshot>.broadcast();
  AuthSession? lastSession;

  @override
  ConnectionSnapshot get snapshot => const ConnectionSnapshot.idle();

  @override
  Stream<ConnectionSnapshot> get connectionSnapshots => _snapshots.stream;

  @override
  Stream<ChatMessageItem> get inboundMessages =>
      const Stream<ChatMessageItem>.empty();

  @override
  Future<void> connect(AuthSession session) async {
    lastSession = session;
    if (connectError != null) {
      throw connectError!;
    }
  }

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
}
