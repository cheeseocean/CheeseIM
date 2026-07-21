package com.cheeseocean.im.common.core.store.idempotency.message.rocksdb;

import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxStore;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * all-in-one 模式的消息发送 inbox；同步方法保证单进程内 claim 与状态迁移原子。
 */
public class RocksDbMessageSendInboxStore implements MessageSendInboxStore {

    private final RocksDbSupport support;
    private final Duration ttl;
    private final long leaseMillis;

    public RocksDbMessageSendInboxStore(Path dataDirectory,
                                        ObjectMapper objectMapper,
                                        MessageSendInboxProperties properties) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("message-send-inbox"),
                Objects.requireNonNull(objectMapper, "objectMapper"));
        Objects.requireNonNull(properties, "properties");
        this.ttl = Duration.ofSeconds(properties.normalizedTtlSeconds());
        this.leaseMillis = properties.normalizedLeaseMillis();
    }

    @Override
    public synchronized Claim claim(String key,
                                    String payloadFingerprint,
                                    String proposedServerMsgId,
                                    String ownerToken,
                                    long nowMillis) {
        State state = support.get(key, State.class);
        if (state == null) {
            State created = new State(
                    payloadFingerprint,
                    proposedServerMsgId,
                    "PENDING",
                    ownerToken,
                    nowMillis + leaseMillis,
                    nowMillis,
                    0L,
                    null);
            support.put(key, created, ttl);
            return claim(ClaimStatus.ACQUIRED, created);
        }
        if (!Objects.equals(state.fingerprint(), payloadFingerprint)) {
            return claim(ClaimStatus.CONFLICT, state);
        }
        if ("ACCEPTED".equals(state.status())) {
            support.put(key, state, ttl);
            return claim(ClaimStatus.ACCEPTED, state);
        }
        if (state.leaseUntil() <= nowMillis) {
            State acquired = new State(
                    state.fingerprint(),
                    state.serverMsgId(),
                    "PENDING",
                    ownerToken,
                    nowMillis + leaseMillis,
                    state.createdAt(),
                    0L,
                    state.effectiveOfflinePush());
            support.put(key, acquired, ttl);
            return claim(ClaimStatus.ACQUIRED, acquired);
        }
        return claim(ClaimStatus.IN_PROGRESS, state);
    }

    @Override
    public synchronized boolean bindEffectiveOfflinePush(String key,
                                                         String ownerToken,
                                                         boolean needOfflinePush) {
        State state = support.get(key, State.class);
        if (state == null
                || !"PENDING".equals(state.status())
                || !Objects.equals(state.owner(), ownerToken)) {
            throw new IllegalStateException("Message send inbox policy binding lost its active lease");
        }
        boolean stableValue = state.effectiveOfflinePush() == null
                ? needOfflinePush
                : state.effectiveOfflinePush();
        support.put(key, new State(
                state.fingerprint(),
                state.serverMsgId(),
                state.status(),
                state.owner(),
                state.leaseUntil(),
                state.createdAt(),
                state.acceptedAt(),
                stableValue), ttl);
        return stableValue;
    }

    @Override
    public synchronized long markAccepted(String key,
                                          String payloadFingerprint,
                                          String serverMsgId,
                                          long acceptedAt) {
        State state = support.get(key, State.class);
        if (state == null
                || !Objects.equals(state.fingerprint(), payloadFingerprint)
                || !Objects.equals(state.serverMsgId(), serverMsgId)) {
            throw new IllegalStateException("Message send inbox disappeared or changed before broker ACK");
        }
        long stableAcceptedAt = state.acceptedAt() > 0 ? state.acceptedAt() : acceptedAt;
        support.put(key, new State(
                state.fingerprint(),
                state.serverMsgId(),
                "ACCEPTED",
                "",
                0L,
                state.createdAt(),
                stableAcceptedAt,
                state.effectiveOfflinePush()), ttl);
        return stableAcceptedAt;
    }

    @Override
    public synchronized void release(String key, String ownerToken) {
        State state = support.get(key, State.class);
        if (state == null
                || !"PENDING".equals(state.status())
                || !Objects.equals(state.owner(), ownerToken)) {
            return;
        }
        support.put(key, new State(
                state.fingerprint(),
                state.serverMsgId(),
                state.status(),
                "",
                0L,
                state.createdAt(),
                state.acceptedAt(),
                state.effectiveOfflinePush()), ttl);
    }

    private static Claim claim(ClaimStatus status, State state) {
        return new Claim(
                status,
                state.serverMsgId(),
                state.createdAt(),
                state.acceptedAt(),
                state.effectiveOfflinePush());
    }

    private record State(String fingerprint,
                         String serverMsgId,
                         String status,
                         String owner,
                         long leaseUntil,
                         long createdAt,
                         long acceptedAt,
                         Boolean effectiveOfflinePush) {
    }
}
