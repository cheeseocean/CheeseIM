import 'package:flutter_test/flutter_test.dart';
import 'package:im_flutter_client/src/features/chat/send_error_message.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

void main() {
  test('maps permission errors to a user-facing message', () {
    expect(
      formatSendErrorMessage(Exception('permission denied')),
      'You do not have permission to send this message.',
    );
  });

  test('maps parameter errors to a user-facing message', () {
    expect(
      formatSendErrorMessage(Exception('param error: invalid recvID')),
      'The message request is invalid.',
    );
  });

  test('maps server errors to a user-facing message', () {
    expect(
      formatSendErrorMessage(Exception('internal server error')),
      'The server is temporarily unavailable.',
    );
  });

  test('maps disconnected errors to a user-facing message', () {
    expect(
      formatSendErrorMessage(Exception('socket disconnected')),
      'The connection was lost.',
    );
  });

  test('prefers structured send error kind over the raw message', () {
    expect(
      formatSendErrorMessage(
        const SendMessageException(
          kind: SendMessageErrorKind.connectionLost,
          message: 'completely different raw message',
        ),
      ),
      'The connection was lost.',
    );
  });
}
