import 'package:im_tcp_sdk/im_tcp_sdk.dart';

String formatSendErrorMessage(Object error) {
  if (error is SendMessageException) {
    switch (error.kind) {
      case SendMessageErrorKind.permissionDenied:
        return 'You do not have permission to send this message.';
      case SendMessageErrorKind.invalidRequest:
        return 'The message request is invalid.';
      case SendMessageErrorKind.serverUnavailable:
        return 'The server is temporarily unavailable.';
      case SendMessageErrorKind.connectionLost:
        return 'The connection was lost.';
      case SendMessageErrorKind.unknown:
        return 'Send failed.';
    }
  }

  final raw = error.toString().replaceFirst(RegExp(r'^[^:]+: '), '').trim().toLowerCase();

  if (raw.contains('permission') ||
      raw.contains('forbidden') ||
      raw.contains('unauthorized')) {
    return 'You do not have permission to send this message.';
  }

  if (raw.contains('param') ||
      raw.contains('invalid') ||
      raw.contains('bad request')) {
    return 'The message request is invalid.';
  }

  if (raw.contains('connection closed') ||
      raw.contains('disconnected') ||
      raw.contains('not connected') ||
      raw.contains('connection was lost') ||
      raw.contains('socket disconnected')) {
    return 'The connection was lost.';
  }

  if (raw.contains('internal') ||
      raw.contains('server') ||
      raw.contains('timeout') ||
      raw.contains('temporarily unavailable')) {
    return 'The server is temporarily unavailable.';
  }

  return 'Send failed.';
}
