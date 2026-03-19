import 'package:im_tcp_sdk/src/protocol/cheese_message.dart';
import 'package:im_tcp_sdk/src/protocol/message_types.dart';
import 'package:test/test.dart';

void main() {
  test('exposes message type mapping expected by the gateway contract', () {
    expect(TcpMessageTypes.connectReq, 1);
    expect(TcpMessageTypes.connectSuccess, 2);
    expect(TcpMessageTypes.connectFailed, 3);
    expect(TcpMessageTypes.authReq, 10);
    expect(TcpMessageTypes.authSuccess, 11);
    expect(TcpMessageTypes.authFailed, 12);
    expect(TcpMessageTypes.heartbeatReq, 20);
    expect(TcpMessageTypes.heartbeatResp, 21);
    expect(TcpMessageTypes.sendMsgReq, 30);
    expect(TcpMessageTypes.sendMsgResp, 31);
    expect(TcpMessageTypes.recvMsgNotify, 32);
    expect(TcpMessageTypes.errorResp, 90);
    expect(TcpMessageTypes.paramError, 91);
    expect(TcpMessageTypes.permissionError, 92);
    expect(TcpMessageTypes.internalError, 93);
  });

  test('encodes and decodes a CheeseMessage frame', () {
    final frame = CheeseMessage(
      msgType: TcpMessageTypes.authReq,
      operationId: 'op-auth-00000001',
      timestamp: 1710000000000,
      data: '{"token":"jwt-token"}',
    );

    final decoded = CheeseMessage.decode(frame.encode());

    expect(decoded.msgType, TcpMessageTypes.authReq);
    expect(decoded.operationId, 'op-auth-00000001');
    expect(decoded.timestamp, 1710000000000);
    expect(decoded.data, '{"token":"jwt-token"}');
  });
}
