package com.cheeseocean.im.common.api.session;

public interface SessionRevocationService {

    void revokeSession(String sessionId, String reason);

    void revokeUserSessions(String userId, String reason);

    void revokeDeviceSession(String userId, String deviceId, String reason);
}
