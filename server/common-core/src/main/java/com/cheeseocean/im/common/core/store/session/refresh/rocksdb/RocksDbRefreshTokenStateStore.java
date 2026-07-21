package com.cheeseocean.im.common.core.store.session.refresh.rocksdb;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenCodec;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * all-in-one 模式的 refresh token family 状态机。
 */
public class RocksDbRefreshTokenStateStore implements RefreshTokenStateStore {

    private final RocksDbSupport support;

    public RocksDbRefreshTokenStateStore(Path dataDirectory, ObjectMapper objectMapper) {
        support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("refresh-token"),
                Objects.requireNonNull(objectMapper, "objectMapper"));
    }

    @Override
    public synchronized IssuedToken createFamily(String sessionId, long ttlMs, long nowMillis) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId required");
        }
        long stableTtlMs = Math.max(1_000L, ttlMs);
        for (int attempt = 0; attempt < 3; attempt++) {
            String familyId = RefreshTokenCodec.newFamilyId();
            String key = RedisKeys.refreshTokenFamily(familyId);
            if (support.get(key, State.class) != null) {
                continue;
            }
            String token = RefreshTokenCodec.issue(familyId);
            long expiresAt = nowMillis + stableTtlMs;
            State state = new State(
                    sessionId,
                    "ACTIVE",
                    RefreshTokenCodec.hash(token),
                    0L,
                    expiresAt,
                    new LinkedHashMap<>());
            support.put(key, state, Duration.ofMillis(stableTtlMs));
            return new IssuedToken(familyId, token, sessionId, expiresAt);
        }
        throw new IllegalStateException("Failed to allocate unique refresh token family");
    }

    @Override
    public synchronized Inspection inspect(String refreshToken, long nowMillis) {
        String familyId = RefreshTokenCodec.familyId(refreshToken);
        if (familyId == null) {
            return invalidInspection();
        }
        State state = state(familyId, nowMillis);
        if (state == null) {
            return invalidInspection();
        }
        if ("COMPROMISED".equals(state.status())) {
            return new Inspection(TokenStatus.REUSED, familyId, state.sessionId(), state.expiresAt());
        }
        if (!"ACTIVE".equals(state.status())) {
            return new Inspection(TokenStatus.REVOKED, familyId, state.sessionId(), state.expiresAt());
        }
        String tokenHash = RefreshTokenCodec.hash(refreshToken);
        if (Objects.equals(tokenHash, state.currentHash())) {
            return new Inspection(TokenStatus.CURRENT, familyId, state.sessionId(), state.expiresAt());
        }
        if (state.usedHashes().containsKey(tokenHash)) {
            return new Inspection(TokenStatus.REUSED, familyId, state.sessionId(), state.expiresAt());
        }
        return invalidInspection();
    }

    @Override
    public synchronized Rotation rotate(String refreshToken, long nowMillis) {
        String familyId = RefreshTokenCodec.familyId(refreshToken);
        if (familyId == null) {
            return invalidRotation();
        }
        State state = state(familyId, nowMillis);
        if (state == null) {
            return invalidRotation();
        }
        if (!"ACTIVE".equals(state.status())) {
            return result(RotationStatus.REVOKED, familyId, state, null);
        }
        String tokenHash = RefreshTokenCodec.hash(refreshToken);
        if (Objects.equals(tokenHash, state.currentHash())) {
            String nextToken = RefreshTokenCodec.issue(familyId);
            Map<String, Long> used = new LinkedHashMap<>(state.usedHashes());
            used.put(tokenHash, state.generation());
            State rotated = new State(
                    state.sessionId(),
                    state.status(),
                    RefreshTokenCodec.hash(nextToken),
                    state.generation() + 1L,
                    state.expiresAt(),
                    used);
            put(familyId, rotated, nowMillis);
            return result(RotationStatus.ROTATED, familyId, rotated, nextToken);
        }
        if (state.usedHashes().containsKey(tokenHash)) {
            State compromised = new State(
                    state.sessionId(),
                    "COMPROMISED",
                    state.currentHash(),
                    state.generation(),
                    state.expiresAt(),
                    state.usedHashes());
            put(familyId, compromised, nowMillis);
            return result(RotationStatus.REUSED, familyId, compromised, null);
        }
        return invalidRotation();
    }

    @Override
    public synchronized void revokeFamily(String familyId) {
        if (!RefreshTokenCodec.isFamilyId(familyId)) {
            return;
        }
        State state = state(familyId, System.currentTimeMillis());
        if (state == null) {
            return;
        }
        put(familyId, new State(
                state.sessionId(),
                "REVOKED",
                state.currentHash(),
                state.generation(),
                state.expiresAt(),
                state.usedHashes()), System.currentTimeMillis());
    }

    private State state(String familyId, long nowMillis) {
        String key = RedisKeys.refreshTokenFamily(familyId);
        State state = support.get(key, State.class);
        if (state != null && state.expiresAt() <= nowMillis) {
            support.delete(key);
            return null;
        }
        return state;
    }

    private void put(String familyId, State state, long nowMillis) {
        support.put(
                RedisKeys.refreshTokenFamily(familyId),
                state,
                Duration.ofMillis(Math.max(1L, state.expiresAt() - nowMillis)));
    }

    private static Rotation result(RotationStatus status,
                                   String familyId,
                                   State state,
                                   String refreshToken) {
        return new Rotation(
                status,
                familyId,
                state.sessionId(),
                refreshToken,
                state.expiresAt(),
                state.generation());
    }

    private static Inspection invalidInspection() {
        return new Inspection(TokenStatus.INVALID, null, null, 0L);
    }

    private static Rotation invalidRotation() {
        return new Rotation(RotationStatus.INVALID, null, null, null, 0L, 0L);
    }

    private record State(String sessionId,
                         String status,
                         String currentHash,
                         long generation,
                         long expiresAt,
                         Map<String, Long> usedHashes) {
    }
}
