import { SendMessageError } from '../../transport/sendMessageError';

export function formatSendError(error: unknown): string {
  if (error instanceof SendMessageError) {
    switch (error.kind) {
      case 'permissionDenied':
        return 'You do not have permission to send this message.';
      case 'invalidRequest':
        return 'The message request is invalid.';
      case 'serverUnavailable':
        return 'The server is temporarily unavailable.';
      case 'connectionLost':
        return 'The connection was lost.';
      case 'unknown':
        return 'Send failed.';
    }
  }

  const raw =
    error instanceof Error ? error.message.trim().toLowerCase() : String(error).trim().toLowerCase();

  if (
    raw.includes('permission') ||
    raw.includes('forbidden') ||
    raw.includes('unauthorized')
  ) {
    return 'You do not have permission to send this message.';
  }

  if (
    raw.includes('param') ||
    raw.includes('invalid') ||
    raw.includes('bad request')
  ) {
    return 'The message request is invalid.';
  }

  if (
    raw.includes('connection closed') ||
    raw.includes('disconnected') ||
    raw.includes('not connected') ||
    raw.includes('connection was lost')
  ) {
    return 'The connection was lost.';
  }

  if (
    raw.includes('internal') ||
    raw.includes('server') ||
    raw.includes('timeout') ||
    raw.includes('temporarily unavailable')
  ) {
    return 'The server is temporarily unavailable.';
  }

  return 'Send failed.';
}
