import 'package:im_tcp_sdk/im_tcp_sdk.dart';

abstract interface class ImClientGateway {
  ConnectionSnapshot get snapshot;

  Stream<ConnectionSnapshot> get connectionSnapshots;

  Stream<ChatMessageItem> get inboundMessages;

  Future<void> connect(AuthSession session);

  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  });

  Future<void> dispose();
}

final class SdkImClientGateway implements ImClientGateway {
  SdkImClientGateway(this._client);

  final ImTcpClient _client;

  @override
  ConnectionSnapshot get snapshot => _client.snapshot;

  @override
  Stream<ConnectionSnapshot> get connectionSnapshots =>
      _client.connectionSnapshots;

  @override
  Stream<ChatMessageItem> get inboundMessages =>
      _client.inboundMessages;

  @override
  Future<void> connect(AuthSession session) {
    return _client.connect(session);
  }

  @override
  Future<ChatMessageItem> sendTextMessage({
    required String peerId,
    required String text,
  }) {
    return _client.sendTextMessage(peerId: peerId, text: text);
  }

  @override
  Future<void> dispose() {
    return _client.dispose();
  }
}

final class AppDependencies {
  const AppDependencies({required this.client});

  final ImClientGateway client;
}

Future<AppDependencies> bootstrap() async {
  final transport = ImTcpConnection();
  final sessionManager = ImTcpSessionManager(transport: transport);
  final client = ImTcpClient(sessionManager: sessionManager);
  return AppDependencies(client: SdkImClientGateway(client));
}
