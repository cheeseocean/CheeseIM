import { describe, expect, it } from 'vitest';

import { formatSendError } from '../features/chat/sendError';
import { commandTypes } from '../transport/envelope';
import { classifySendMessageError, SendMessageError } from '../transport/sendMessageError';

describe('formatSendError', () => {
  it('maps permission errors to a user-facing message', () => {
    expect(formatSendError(new Error('permission denied'))).toBe(
      'You do not have permission to send this message.',
    );
  });

  it('maps parameter errors to a user-facing message', () => {
    expect(formatSendError(new Error('param error: invalid recvID'))).toBe(
      'The message request is invalid.',
    );
  });

  it('maps server errors to a user-facing message', () => {
    expect(formatSendError(new Error('internal server error'))).toBe(
      'The server is temporarily unavailable.',
    );
  });

  it('maps disconnected errors to a user-facing message', () => {
    expect(formatSendError(new Error('WebSocket connection closed.'))).toBe(
      'The connection was lost.',
    );
  });

  it('prefers structured send error kind over the raw message', () => {
    expect(
      formatSendError(
        new SendMessageError('permissionDenied', 'completely different raw message'),
      ),
    ).toBe('You do not have permission to send this message.');
  });
});

describe('classifySendMessageError', () => {
  it('classifies envelope error command 90 as an invalid request when the message says bad request', () => {
    expect(classifySendMessageError('bad request', commandTypes.error)).toMatchObject({
      kind: 'invalidRequest',
      message: 'bad request',
    });
  });

  it('classifies envelope error command 90 as permission denied when the message says forbidden', () => {
    expect(classifySendMessageError('forbidden', commandTypes.error)).toMatchObject({
      kind: 'permissionDenied',
      message: 'forbidden',
    });
  });

  it('classifies envelope error command 90 as server unavailable when the message says internal server error', () => {
    expect(classifySendMessageError('internal server error', commandTypes.error)).toMatchObject({
      kind: 'serverUnavailable',
      message: 'internal server error',
    });
  });

  it('does not rely on legacy websocket msgType values for error classification', () => {
    expect(classifySendMessageError('unexpected gateway response', 9002)).toMatchObject({
      kind: 'unknown',
      message: 'unexpected gateway response',
    });
  });
});
