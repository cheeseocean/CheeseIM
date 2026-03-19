enum SendMessageErrorKind {
  permissionDenied,
  invalidRequest,
  serverUnavailable,
  connectionLost,
  unknown,
}

final class SendMessageException implements Exception {
  const SendMessageException({
    required this.kind,
    required this.message,
  });

  final SendMessageErrorKind kind;
  final String message;

  @override
  String toString() => message;
}

SendMessageException classifySendMessageException(String message) {
  final raw = message.trim().toLowerCase();

  if (raw.contains('permission') ||
      raw.contains('forbidden') ||
      raw.contains('unauthorized')) {
    return SendMessageException(
      kind: SendMessageErrorKind.permissionDenied,
      message: message,
    );
  }

  if (raw.contains('param') ||
      raw.contains('invalid') ||
      raw.contains('bad request')) {
    return SendMessageException(
      kind: SendMessageErrorKind.invalidRequest,
      message: message,
    );
  }

  if (raw.contains('connection closed') ||
      raw.contains('disconnected') ||
      raw.contains('not connected') ||
      raw.contains('connection was lost') ||
      raw.contains('session is not connected')) {
    return SendMessageException(
      kind: SendMessageErrorKind.connectionLost,
      message: message,
    );
  }

  if (raw.contains('internal') ||
      raw.contains('server') ||
      raw.contains('timeout') ||
      raw.contains('temporarily unavailable')) {
    return SendMessageException(
      kind: SendMessageErrorKind.serverUnavailable,
      message: message,
    );
  }

  return SendMessageException(
    kind: SendMessageErrorKind.unknown,
    message: message,
  );
}
