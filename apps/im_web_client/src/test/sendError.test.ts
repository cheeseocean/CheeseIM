import { describe, expect, it } from 'vitest';

import { formatSendError } from '../features/chat/sendError';
import { SendMessageError } from '../transport/sendMessageError';

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
