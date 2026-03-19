final class TcpMessageTypes {
  static const int connectReq = 1;
  static const int connectSuccess = 2;
  static const int connectFailed = 3;

  static const int authReq = 10;
  static const int authSuccess = 11;
  static const int authFailed = 12;

  static const int heartbeatReq = 20;
  static const int heartbeatResp = 21;

  static const int sendMsgReq = 30;
  static const int sendMsgResp = 31;
  static const int recvMsgNotify = 32;

  static const int errorResp = 90;
  static const int paramError = 91;
  static const int permissionError = 92;
  static const int internalError = 93;

  const TcpMessageTypes._();
}
