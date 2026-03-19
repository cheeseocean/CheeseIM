final class AuthSession {
  const AuthSession({
    required this.host,
    required this.port,
    required this.userId,
    required this.platformId,
    required this.token,
  });

  final String host;
  final int port;
  final String userId;
  final int platformId;
  final String token;
}
