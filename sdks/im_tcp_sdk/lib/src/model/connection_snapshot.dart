enum ConnectionLifecycle {
  idle,
  connecting,
  connected,
  authenticating,
  ready,
  reconnecting,
  closed,
}

enum ClientErrorKind {
  authRejected,
  forceLogout,
  permissionDenied,
  networkInterrupted,
  unknown,
}

const Object _unsetConnectionSnapshotField = Object();

final class ConnectionSnapshot {
  const ConnectionSnapshot({
    required this.state,
    this.reconnectAttempt = 0,
    this.lastHeartbeatSuccessAt,
    this.lastError,
    this.errorKind,
  });

  const ConnectionSnapshot.idle() : this(state: ConnectionLifecycle.idle);

  final ConnectionLifecycle state;
  final int reconnectAttempt;
  final int? lastHeartbeatSuccessAt;
  final String? lastError;
  final ClientErrorKind? errorKind;

  ConnectionSnapshot copyWith({
    ConnectionLifecycle? state,
    int? reconnectAttempt,
    int? lastHeartbeatSuccessAt,
    Object? lastError = _unsetConnectionSnapshotField,
    Object? errorKind = _unsetConnectionSnapshotField,
  }) {
    return ConnectionSnapshot(
      state: state ?? this.state,
      reconnectAttempt: reconnectAttempt ?? this.reconnectAttempt,
      lastHeartbeatSuccessAt:
          lastHeartbeatSuccessAt ?? this.lastHeartbeatSuccessAt,
      lastError: identical(lastError, _unsetConnectionSnapshotField)
          ? this.lastError
          : lastError as String?,
      errorKind: identical(errorKind, _unsetConnectionSnapshotField)
          ? this.errorKind
          : errorKind as ClientErrorKind?,
    );
  }
}
