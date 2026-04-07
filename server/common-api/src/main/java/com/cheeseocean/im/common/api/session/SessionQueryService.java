package com.cheeseocean.im.common.api.session;


public interface SessionQueryService {

    SessionPrincipal getByAccessToken(String accessToken);

    SessionPrincipal getBySessionId(String sessionId);

    boolean isSessionValid(String sessionId);

    boolean isUserBanned(String userId);

    boolean matchesTokenVersion(String sessionId, Long tokenVersion);
}
