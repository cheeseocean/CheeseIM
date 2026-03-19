import 'dart:async';

import 'package:im_tcp_sdk/src/client/im_tcp_session_manager.dart';
import 'package:im_tcp_sdk/src/model/auth_session.dart';
import 'package:im_tcp_sdk/src/model/connection_snapshot.dart';
import 'package:im_tcp_sdk/src/protocol/cheese_message.dart';
import 'package:im_tcp_sdk/src/protocol/message_types.dart';
import 'package:test/test.dart';

void main() {
  test('moves idle -> connecting -> authenticating -> ready', () async {
    final transport = _FakeTransportConnection(
      onSend: (message, transport) {
        if (message.msgType == TcpMessageTypes.authReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.authSuccess,
              operationId: message.operationId,
              timestamp: 1710000000001,
              data: '{"userID":"user123","message":"认证成功"}',
            ),
          );
        }
      },
    );
    final manager = ImTcpSessionManager(transport: transport);

    expect(manager.snapshot.state, ConnectionLifecycle.idle);

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    expect(manager.snapshot.state, ConnectionLifecycle.ready);
  });

  test('retries with capped backoff', () {
    final delays = <int>[];
    final manager = ImTcpSessionManager(
      transport: _FakeTransportConnection(),
      reconnectScheduler: (delay) async {
        delays.add(delay.inSeconds);
      },
    );

    expect(
      List.generate(6, manager.reconnectDelaySecondsForAttempt),
      [1, 2, 5, 10, 20, 20],
    );

    manager.debugScheduleReconnectForAttempt(0);
    manager.debugScheduleReconnectForAttempt(1);
    manager.debugScheduleReconnectForAttempt(2);
    manager.debugScheduleReconnectForAttempt(3);
    manager.debugScheduleReconnectForAttempt(4);
    manager.debugScheduleReconnectForAttempt(5);

    expect(delays, [1, 2, 5, 10, 20, 20]);
  });

  test('enters reconnecting after heartbeat timeout', () async {
    final transport = _FakeTransportConnection(
      onSend: (message, transport) {
        if (message.msgType == TcpMessageTypes.authReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.authSuccess,
              operationId: message.operationId,
              timestamp: 1710000000001,
              data: '{"userID":"user123","message":"认证成功"}',
            ),
          );
        }
      },
    );
    final manager = ImTcpSessionManager(
      transport: transport,
      reconnectScheduler: (_) async {},
    );

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    await manager.sendHeartbeat();
    await manager.handleHeartbeatTimeout();

    expect(transport.openCount, 2);
    expect(manager.snapshot.reconnectAttempt, 1);
  });

  test('marks auth rejection as a normalized auth error', () async {
    final transport = _FakeTransportConnection(
      onSend: (message, transport) {
        if (message.msgType == TcpMessageTypes.authReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.authFailed,
              operationId: message.operationId,
              timestamp: 1710000000001,
              data: 'Authentication failed',
            ),
          );
        }
      },
    );
    final manager = ImTcpSessionManager(transport: transport);

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'bad-token',
      ),
    );

    expect(manager.snapshot.state, ConnectionLifecycle.closed);
    expect(manager.snapshot.errorKind, ClientErrorKind.authRejected);
    expect(manager.snapshot.lastError, 'Authentication failed');
  });

  test('marks transport open failures as normalized network errors', () async {
    final transport = _FakeTransportConnection(
      openError: StateError('socket closed'),
    );
    final manager = ImTcpSessionManager(transport: transport);

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    expect(manager.snapshot.state, ConnectionLifecycle.closed);
    expect(manager.snapshot.errorKind, ClientErrorKind.networkInterrupted);
    expect(manager.snapshot.lastError, 'socket closed');
  });

  test('serializes overlapping reconnect triggers into one attempt', () async {
    final reconnectGate = Completer<void>();
    final transport = _FakeTransportConnection();
    final manager = ImTcpSessionManager(
      transport: transport,
      reconnectScheduler: (_) => reconnectGate.future,
    );

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    transport.emitDisconnect();
    transport.emitDisconnect();
    await Future<void>.delayed(Duration.zero);

    expect(manager.snapshot.reconnectAttempt, 1);
    expect(transport.openCount, 1);

    reconnectGate.complete();
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);

    expect(transport.openCount, 2);
  });

  test('starts automatic heartbeats after ready', () async {
    final transport = _FakeTransportConnection();
    final manager = ImTcpSessionManager(
      transport: transport,
      heartbeatInterval: const Duration(milliseconds: 1),
    );

    await manager.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    await Future<void>.delayed(const Duration(milliseconds: 5));

    expect(transport.sentTypes, contains(TcpMessageTypes.heartbeatReq));
  });
}

final class _FakeTransportConnection implements TransportConnection {
  _FakeTransportConnection({this.onSend, this.openError});

  final void Function(
      CheeseMessage message, _FakeTransportConnection transport)? onSend;
  final Object? openError;
  final StreamController<CheeseMessage> _incoming =
      StreamController<CheeseMessage>.broadcast();
  final StreamController<void> _disconnects =
      StreamController<void>.broadcast();
  bool opened = false;
  int openCount = 0;
  final List<int> sentTypes = <int>[];

  @override
  Stream<CheeseMessage> get messages => _incoming.stream;

  @override
  Stream<void> get disconnects => _disconnects.stream;

  @override
  Future<void> open(String host, int port) async {
    if (openError != null) {
      throw openError!;
    }
    opened = true;
    openCount += 1;
    emitIncoming(
      CheeseMessage(
        msgType: TcpMessageTypes.connectSuccess,
        operationId: 'system',
        timestamp: 1710000000000,
        data: '连接成功',
      ),
    );
  }

  @override
  Future<void> send(CheeseMessage message) async {
    sentTypes.add(message.msgType);
    if (message.msgType == TcpMessageTypes.heartbeatReq && onSend == null) {
      emitIncoming(
        CheeseMessage(
          msgType: TcpMessageTypes.heartbeatResp,
          operationId: message.operationId,
          timestamp: 1710000000002,
          data: 'pong',
        ),
      );
      return;
    }
    if (message.msgType == TcpMessageTypes.authReq && onSend == null) {
      emitIncoming(
        CheeseMessage(
          msgType: TcpMessageTypes.authSuccess,
          operationId: message.operationId,
          timestamp: 1710000000001,
          data: '{"userID":"user123","message":"认证成功"}',
        ),
      );
      return;
    }
    onSend?.call(message, this);
  }

  @override
  Future<void> close() async {
    opened = false;
  }

  void emitIncoming(CheeseMessage message) {
    _incoming.add(message);
  }

  void emitDisconnect() {
    _disconnects.add(null);
  }
}
