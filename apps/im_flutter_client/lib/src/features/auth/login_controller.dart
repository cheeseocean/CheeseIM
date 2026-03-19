import 'package:flutter/foundation.dart';
import 'package:im_tcp_sdk/im_tcp_sdk.dart';

import '../../core/bootstrap.dart';
import 'business_login_adapter.dart';

final class LoginController extends ChangeNotifier {
  LoginController(this._client);

  final ImClientGateway _client;

  bool _submitting = false;
  String? _errorMessage;

  bool get submitting => _submitting;
  String? get errorMessage => _errorMessage;

  Future<void> login({
    required String host,
    required int port,
    required String userId,
    required String token,
    required int platformId,
  }) async {
    await _connect(
      AuthSession(
        host: host,
        port: port,
        userId: userId,
        platformId: platformId,
        token: token,
      ),
    );
  }

  Future<void> loginWithBusinessProvider(BusinessLoginAdapter adapter) async {
    await adapter.login();
  }

  Future<void> _connect(AuthSession session) async {
    _submitting = true;
    _errorMessage = null;
    notifyListeners();
    try {
      await _client.connect(session);
    } on Object catch (error) {
      _errorMessage = error.toString().replaceFirst(RegExp(r'^[^:]+: '), '');
    } finally {
      _submitting = false;
      notifyListeners();
    }
  }
}
