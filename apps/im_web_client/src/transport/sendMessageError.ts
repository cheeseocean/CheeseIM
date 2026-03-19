export type SendMessageErrorKind =
  | 'permissionDenied'
  | 'invalidRequest'
  | 'serverUnavailable'
  | 'connectionLost'
  | 'unknown';

export class SendMessageError extends Error {
  constructor(
    readonly kind: SendMessageErrorKind,
    message: string,
  ) {
    super(message);
    this.name = 'SendMessageError';
  }
}

export function classifySendMessageError(
  message: string,
  msgType?: number,
): SendMessageError {
  switch (msgType) {
    case 9002:
      return new SendMessageError('invalidRequest', message);
    case 9003:
      return new SendMessageError('permissionDenied', message);
    case 9004:
      return new SendMessageError('serverUnavailable', message);
  }

  const raw = message.trim().toLowerCase();
  if (
    raw.includes('permission') ||
    raw.includes('forbidden') ||
    raw.includes('unauthorized')
  ) {
    return new SendMessageError('permissionDenied', message);
  }
  if (
    raw.includes('param') ||
    raw.includes('invalid') ||
    raw.includes('bad request')
  ) {
    return new SendMessageError('invalidRequest', message);
  }
  if (
    raw.includes('connection closed') ||
    raw.includes('disconnected') ||
    raw.includes('not connected') ||
    raw.includes('connection was lost')
  ) {
    return new SendMessageError('connectionLost', message);
  }
  if (
    raw.includes('internal') ||
    raw.includes('server') ||
    raw.includes('timeout') ||
    raw.includes('temporarily unavailable')
  ) {
    return new SendMessageError('serverUnavailable', message);
  }
  return new SendMessageError('unknown', message);
}
