import 'package:im_tcp_sdk/im_tcp_sdk.dart';

abstract interface class BusinessLoginAdapter {
  Future<AuthSession> login();
}
