import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import '../protocol/cheese_message.dart';
import 'im_tcp_session_manager.dart';

typedef SocketConnector = Future<Socket> Function(String host, int port);

final class ImTcpConnection implements TransportConnection {
  ImTcpConnection({SocketConnector? socketConnector})
      : _socketConnector = socketConnector ?? Socket.connect;

  ImTcpConnection.test() : _socketConnector = Socket.connect;

  final SocketConnector _socketConnector;

  final BytesBuilder _buffer = BytesBuilder(copy: false);
  final StreamController<List<CheeseMessage>> _framesController =
      StreamController<List<CheeseMessage>>.broadcast();
  final StreamController<CheeseMessage> _messagesController =
      StreamController<CheeseMessage>.broadcast();
  final StreamController<void> _disconnectsController =
      StreamController<void>.broadcast();
  Socket? _socket;
  StreamSubscription<List<int>>? _socketSubscription;

  Stream<List<CheeseMessage>> get frames => _framesController.stream;

  @override
  Stream<CheeseMessage> get messages => _messagesController.stream;

  @override
  Stream<void> get disconnects => _disconnectsController.stream;

  @override
  Future<void> open(String host, int port) async {
    await close();
    final socket = await _socketConnector(host, port);
    _socket = socket;
    _socketSubscription = socket.listen(
      addIncomingBytes,
      onDone: _emitDisconnect,
      onError: (_, __) => _emitDisconnect(),
      cancelOnError: false,
    );
  }

  @override
  Future<void> send(CheeseMessage message) async {
    final socket = _socket;
    if (socket == null) {
      throw StateError('TCP socket is not connected.');
    }
    socket.add(message.encode());
    await socket.flush();
  }

  void addIncomingBytes(List<int> chunk) {
    _buffer.add(chunk);
    _drainFrames();
  }

  @override
  Future<void> close() async {
    await _socketSubscription?.cancel();
    _socketSubscription = null;
    final socket = _socket;
    if (socket != null) {
      await socket.close();
      socket.destroy();
    }
    _socket = null;
    _buffer.takeBytes();
  }

  Future<void> dispose() async {
    await close();
    await _framesController.close();
    await _messagesController.close();
    await _disconnectsController.close();
  }

  void _drainFrames() {
    final pending = Uint8List.fromList(_buffer.takeBytes());
    if (pending.isEmpty) {
      return;
    }

    var cursor = 0;
    final decoded = <CheeseMessage>[];

    while (pending.length - cursor >= CheeseMessage.headerLength) {
      final frameLength =
          CheeseMessage.peekFrameLength(pending, offset: cursor);
      if (pending.length - cursor < frameLength) {
        break;
      }

      decoded.add(
        CheeseMessage.decode(pending.sublist(cursor, cursor + frameLength)),
      );
      cursor += frameLength;
    }

    if (cursor < pending.length) {
      _buffer.add(pending.sublist(cursor));
    }

    if (decoded.isNotEmpty) {
      _framesController.add(decoded);
      for (final message in decoded) {
        _messagesController.add(message);
      }
    }
  }

  void _emitDisconnect() {
    _socket = null;
    if (!_disconnectsController.isClosed) {
      _disconnectsController.add(null);
    }
  }
}
