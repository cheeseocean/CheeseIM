import 'dart:async';

import 'package:im_tcp_sdk/src/client/im_tcp_client.dart';
import 'package:im_tcp_sdk/src/client/im_tcp_session_manager.dart';
import 'package:im_tcp_sdk/src/model/auth_session.dart';
import 'package:im_tcp_sdk/src/model/message_delivery_status.dart';
import 'package:im_tcp_sdk/src/model/send_message_exception.dart';
import 'package:im_tcp_sdk/src/protocol/cheese_message.dart';
import 'package:im_tcp_sdk/src/protocol/message_types.dart';
import 'package:test/test.dart';

void main() {
  test('sendTextMessage returns a sent outgoing message after ack', () async {
    final transport = _FakeTransportConnection(
      onSend: (message, transport) {
        if (message.msgType == TcpMessageTypes.authReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.authSuccess,
              operationId: message.operationId,
              timestamp: 1710000000001,
              data: '{"userID":"user123","message":"认证成功"}',
            ),
          );
          return;
        }
        if (message.msgType == TcpMessageTypes.sendMsgReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.sendMsgResp,
              operationId: message.operationId,
              timestamp: 1710000000002,
              data:
                  '{"serverMsgID":"server-1","clientMsgID":"client-1","sendTime":1710000000002}',
            ),
          );
        }
      },
    );
    final manager = ImTcpSessionManager(transport: transport);
    final client = ImTcpClient(sessionManager: manager);

    await client.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    final message = await client.sendTextMessage(peerId: 'u2', text: 'hello');

    expect(message.serverMsgId, 'server-1');
    expect(message.status, MessageDeliveryStatus.sent);
    expect(message.isOutgoing, isTrue);
  });

  test('emits inboundMessages from recv notify payloads', () async {
    final transport = _FakeTransportConnection(
      onSend: (message, transport) {
        if (message.msgType == TcpMessageTypes.authReq) {
          transport.emitIncoming(
            CheeseMessage(
              msgType: TcpMessageTypes.authSuccess,
              operationId: message.operationId,
              timestamp: 1710000000001,
              data: '{"userID":"user123","message":"认证成功"}',
            ),
          );
        }
      },
    );
    final manager = ImTcpSessionManager(transport: transport);
    final client = ImTcpClient(sessionManager: manager);

    await client.connect(
      const AuthSession(
        host: '127.0.0.1',
        port: 5148,
        userId: 'user123',
        platformId: 2,
        token: 'jwt-token',
      ),
    );

    final nextMessage = client.inboundMessages.first;
    transport.emitIncoming(
      CheeseMessage(
        msgType: TcpMessageTypes.recvMsgNotify,
        operationId: 'op-notify-1',
        timestamp: 1710000000003,
        data:
            '{"serverMsgID":"server-2","clientMsgID":"client-2","sendID":"u2","recvID":"user123","content":"hi","contentType":101,"chatType":1,"sendTime":1710000000003}',
      ),
    );

    final message = await nextMessage;
    expect(message.peerId, 'u2');
    expect(message.content, 'hi');
    expect(message.status, MessageDeliveryStatus.received);
    expect(message.isOutgoing, isFalse);
  });

  test('sendTextMessage throws a structured connectionLost error when not connected', () async {
    final transport = _FakeTransportConnection();
    final manager = ImTcpSessionManager(transport: transport);
    final client = ImTcpClient(sessionManager: manager);

    expect(
      () => client.sendTextMessage(peerId: 'u2', text: 'hello'),
      throwsA(
        isA<SendMessageException>()
            .having((error) => error.kind, 'kind', SendMessageErrorKind.connectionLost),
      ),
    );
  });
}

final class _FakeTransportConnection implements TransportConnection {
  _FakeTransportConnection({this.onSend});

  final void Function(
      CheeseMessage message, _FakeTransportConnection transport)? onSend;
  final StreamController<CheeseMessage> _incoming =
      StreamController<CheeseMessage>.broadcast();
  final StreamController<void> _disconnects =
      StreamController<void>.broadcast();

  @override
  Stream<CheeseMessage> get messages => _incoming.stream;

  @override
  Stream<void> get disconnects => _disconnects.stream;

  @override
  Future<void> open(String host, int port) async {
    emitIncoming(
      CheeseMessage(
        msgType: TcpMessageTypes.connectSuccess,
        operationId: 'system',
        timestamp: 1710000000000,
        data: '连接成功',
      ),
    );
  }

  @override
  Future<void> send(CheeseMessage message) async {
    onSend?.call(message, this);
  }

  @override
  Future<void> close() async {}

  void emitIncoming(CheeseMessage message) {
    _incoming.add(message);
  }
}
