package com.cheeseocean.im.common.api.auth;

/** authcenter 对外认证契约。 */
public interface AuthenticationService {
    AuthenticationResult login(AuthenticationCommand command);
    AuthenticationResult refresh(String refreshToken);
    void logout(String sessionId);
    void kickoffDevice(String userId, String deviceId);
    void kickoffAll(String userId);
}
