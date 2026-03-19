import 'dart:typed_data';

import 'package:im_tcp_sdk/src/client/im_tcp_connection.dart';
import 'package:im_tcp_sdk/src/protocol/cheese_message.dart';
import 'package:im_tcp_sdk/src/protocol/message_types.dart';
import 'package:test/test.dart';

void main() {
  test('emits complete frames when bytes arrive in chunks', () async {
    final connection = ImTcpConnection.test();
    addTearDown(connection.dispose);

    final first = CheeseMessage(
      msgType: TcpMessageTypes.connectSuccess,
      operationId: 'system',
      timestamp: 1710000000000,
      data: '连接成功',
    ).encode();
    final second = CheeseMessage(
      msgType: TcpMessageTypes.authReq,
      operationId: 'op-auth-00000001',
      timestamp: 1710000000001,
      data: '{"token":"jwt-token","userID":"user123","platformID":2}',
    ).encode();

    final combined = Uint8List(first.length + second.length)
      ..setRange(0, first.length, first)
      ..setRange(first.length, first.length + second.length, second);

    final nextFrames = connection.frames.first;
    connection.addIncomingBytes(combined.sublist(0, 20));
    connection.addIncomingBytes(combined.sublist(20));

    final frames = await nextFrames;
    expect(frames, hasLength(2));
    expect(frames[0].msgType, TcpMessageTypes.connectSuccess);
    expect(frames[0].operationId, 'system');
    expect(frames[1].msgType, TcpMessageTypes.authReq);
    expect(frames[1].operationId, 'op-auth-00000001');
  });

  test('rejects oversized frame headers early', () {
    final invalidHeader = Uint8List(CheeseMessage.headerLength);
    final header = ByteData.sublistView(invalidHeader);
    header.setUint16(0, CheeseMessage.magic);
    header.setUint8(2, CheeseMessage.version);
    header.setUint8(3, TcpMessageTypes.authReq);
    header.setUint32(4, CheeseMessage.maxDataLength + 1);

    expect(
      () => CheeseMessage.peekFrameLength(invalidHeader),
      throwsA(isA<ArgumentError>()),
    );
  });

  test('clears buffered partial frames when the connection closes', () async {
    final connection = ImTcpConnection.test();
    addTearDown(connection.dispose);

    final partial = CheeseMessage(
      msgType: TcpMessageTypes.authReq,
      operationId: 'op-auth-00000001',
      timestamp: 1710000000001,
      data: '{"token":"jwt-token"}',
    ).encode();
    final complete = CheeseMessage(
      msgType: TcpMessageTypes.connectSuccess,
      operationId: 'system',
      timestamp: 1710000000002,
      data: '连接成功',
    ).encode();

    connection.addIncomingBytes(partial.sublist(0, 10));
    await connection.close();

    final nextFrames = connection.frames.first;
    connection.addIncomingBytes(complete);
    final frames = await nextFrames;

    expect(frames, hasLength(1));
    expect(frames.single.msgType, TcpMessageTypes.connectSuccess);
    expect(frames.single.operationId, 'system');
  });
}
