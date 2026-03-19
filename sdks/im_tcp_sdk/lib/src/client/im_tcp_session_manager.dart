import 'dart:async';
import 'dart:convert';

import '../model/auth_session.dart';
import '../model/connection_snapshot.dart';
import '../protocol/cheese_message.dart';
import '../protocol/message_types.dart';
import 'request_tracker.dart';

abstract interface class TransportConnection {
  Stream<CheeseMessage> get messages;

  Stream<void> get disconnects;

  Future<void> open(String host, int port);

  Future<void> send(CheeseMessage message);

  Future<void> close();
}

final class ImTcpSessionManager {
  ImTcpSessionManager({
    required TransportConnection transport,
    Duration requestTimeout = const Duration(seconds: 10),
    Duration heartbeatInterval = const Duration(seconds: 30),
    Future<void> Function(Duration delay)? reconnectScheduler,
  })  : _transport = transport,
        _requestTracker =
            RequestTracker<CheeseMessage>(timeout: requestTimeout),
        _heartbeatInterval = heartbeatInterval,
        _reconnectScheduler = reconnectScheduler ?? Future<void>.delayed {
    _messageSubscription = _transport.messages.listen(_handleMessage);
    _disconnectSubscription = _transport.disconnects.listen((_) {
      unawaited(_beginReconnect());
    });
  }

  final TransportConnection _transport;
  final RequestTracker<CheeseMessage> _requestTracker;
  final Duration _heartbeatInterval;
  final Future<void> Function(Duration delay) _reconnectScheduler;
  final StreamController<ConnectionSnapshot> _snapshotsController =
      StreamController<ConnectionSnapshot>.broadcast();
  final StreamController<CheeseMessage> _inboundMessagesController =
      StreamController<CheeseMessage>.broadcast();
  late final StreamSubscription<CheeseMessage> _messageSubscription;
  late final StreamSubscription<void> _disconnectSubscription;

  ConnectionSnapshot _snapshot = const ConnectionSnapshot.idle();
  AuthSession? _authSession;
  bool _awaitingHeartbeat = false;
  bool _reconnectInFlight = false;
  Timer? _heartbeatTimer;
  int _operationCounter = 0;

  ConnectionSnapshot get snapshot => _snapshot;
  Stream<ConnectionSnapshot> get snapshots => _snapshotsController.stream;
  Stream<CheeseMessage> get inboundMessages =>
      _inboundMessagesController.stream;
  AuthSession? get currentSession => _authSession;

  Future<void> connect(AuthSession session) async {
    _authSession = session;
    _setSnapshot(
      _snapshot.copyWith(
        state: ConnectionLifecycle.connecting,
        lastError: null,
        errorKind: null,
      ),
    );
    try {
      await _transport.open(session.host, session.port);
      _setSnapshot(_snapshot.copyWith(state: ConnectionLifecycle.connected));
      _setSnapshot(
        _snapshot.copyWith(state: ConnectionLifecycle.authenticating),
      );
      await _sendAuth(session);
    } on Object catch (error) {
      _setClosedError(
        _messageFromError(error),
        ClientErrorKind.networkInterrupted,
      );
    }
  }

  int reconnectDelaySecondsForAttempt(int attempt) {
    const schedule = <int>[1, 2, 5, 10, 20];
    return schedule[attempt < schedule.length ? attempt : schedule.length - 1];
  }

  Future<void> sendHeartbeat() async {
    _awaitingHeartbeat = true;
    await _transport.send(
      CheeseMessage(
        msgType: TcpMessageTypes.heartbeatReq,
        operationId: _nextOperationId(),
        data: 'ping',
      ),
    );
  }

  Future<void> handleHeartbeatTimeout() async {
    if (_awaitingHeartbeat) {
      await _beginReconnect();
    }
  }

  Future<void> debugScheduleReconnectForAttempt(int attempt) {
    return _reconnectScheduler(
        Duration(seconds: reconnectDelaySecondsForAttempt(attempt)));
  }

  Future<void> dispose() async {
    _heartbeatTimer?.cancel();
    _requestTracker.dispose();
    await _messageSubscription.cancel();
    await _disconnectSubscription.cancel();
    await _transport.close();
    await _inboundMessagesController.close();
    await _snapshotsController.close();
  }

  Future<CheeseMessage> sendRequest({
    required int msgType,
    required String data,
  }) async {
    final operationId = _nextOperationId();
    final responseFuture = _requestTracker.track(operationId);
    await _transport.send(
      CheeseMessage(
        msgType: msgType,
        operationId: operationId,
        data: data,
      ),
    );
    return responseFuture;
  }

  Future<void> _sendAuth(AuthSession session) async {
    final operationId = _nextOperationId();
    final responseFuture = _requestTracker.track(operationId);
    await _transport.send(
      CheeseMessage(
        msgType: TcpMessageTypes.authReq,
        operationId: operationId,
        data: jsonEncode(<String, Object>{
          'token': session.token,
          'userID': session.userId,
          'platformID': session.platformId,
        }),
      ),
    );
    final response = await responseFuture;
    if (response.msgType == TcpMessageTypes.authSuccess) {
      _setSnapshot(
        _snapshot.copyWith(
          state: ConnectionLifecycle.ready,
          lastError: null,
          errorKind: null,
        ),
      );
      _startHeartbeatLoop();
      return;
    }
    final errorKind = switch (response.msgType) {
      TcpMessageTypes.authFailed => ClientErrorKind.authRejected,
      TcpMessageTypes.permissionError => ClientErrorKind.permissionDenied,
      _ => ClientErrorKind.unknown,
    };
    if (errorKind == ClientErrorKind.authRejected) {
      _authSession = null;
      _heartbeatTimer?.cancel();
      await _transport.close();
    }
    _setClosedError(response.data ?? 'Request failed', errorKind);
  }

  Future<void> _beginReconnect() async {
    if (_reconnectInFlight) {
      return;
    }
    final session = _authSession;
    if (session == null) {
      _setSnapshot(_snapshot.copyWith(state: ConnectionLifecycle.closed));
      return;
    }
    _reconnectInFlight = true;

    final nextAttempt = _snapshot.reconnectAttempt + 1;
    _setSnapshot(
      _snapshot.copyWith(
        state: ConnectionLifecycle.reconnecting,
        reconnectAttempt: nextAttempt,
        errorKind: null,
      ),
    );
    _heartbeatTimer?.cancel();
    _awaitingHeartbeat = false;

    try {
      await _reconnectScheduler(
        Duration(seconds: reconnectDelaySecondsForAttempt(nextAttempt - 1)),
      );
      await _transport.open(session.host, session.port);
      _setSnapshot(_snapshot.copyWith(state: ConnectionLifecycle.connected));
      await _sendAuth(session);
    } on Object catch (error) {
      _setClosedError(
        _messageFromError(error),
        ClientErrorKind.networkInterrupted,
      );
    } finally {
      _reconnectInFlight = false;
    }
  }

  void _handleMessage(CheeseMessage message) {
    switch (message.msgType) {
      case TcpMessageTypes.connectSuccess:
        _setSnapshot(_snapshot.copyWith(state: ConnectionLifecycle.connected));
        return;
      case TcpMessageTypes.authSuccess:
      case TcpMessageTypes.authFailed:
      case TcpMessageTypes.sendMsgResp:
      case TcpMessageTypes.errorResp:
      case TcpMessageTypes.paramError:
      case TcpMessageTypes.permissionError:
      case TcpMessageTypes.internalError:
        _requestTracker.resolve(message.operationId, message);
        return;
      case TcpMessageTypes.heartbeatResp:
        _awaitingHeartbeat = false;
        _setSnapshot(
          _snapshot.copyWith(
            lastHeartbeatSuccessAt: message.timestamp,
            state: ConnectionLifecycle.ready,
          ),
        );
        return;
      case TcpMessageTypes.recvMsgNotify:
        if (!_inboundMessagesController.isClosed) {
          _inboundMessagesController.add(message);
        }
        return;
    }
  }

  void _setSnapshot(ConnectionSnapshot snapshot) {
    _snapshot = snapshot;
    if (!_snapshotsController.isClosed) {
      _snapshotsController.add(snapshot);
    }
  }

  void _setClosedError(String message, ClientErrorKind errorKind) {
    _heartbeatTimer?.cancel();
    _setSnapshot(
      _snapshot.copyWith(
        state: ConnectionLifecycle.closed,
        lastError: message,
        errorKind: errorKind,
      ),
    );
  }

  String _messageFromError(Object error) {
    return error.toString().replaceFirst(RegExp(r'^[^:]+: '), '');
  }

  String _nextOperationId() {
    _operationCounter += 1;
    return 'op-${_operationCounter.toString().padLeft(16, '0')}';
  }

  void _startHeartbeatLoop() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(_heartbeatInterval, (_) {
      unawaited(_runHeartbeatCycle());
    });
  }

  Future<void> _runHeartbeatCycle() async {
    if (_snapshot.state != ConnectionLifecycle.ready || _reconnectInFlight) {
      return;
    }
    await sendHeartbeat();
    unawaited(_enforceHeartbeatTimeout());
  }

  Future<void> _enforceHeartbeatTimeout() async {
    await Future<void>.delayed(_heartbeatInterval);
    if (_awaitingHeartbeat) {
      await _beginReconnect();
    }
  }
}
