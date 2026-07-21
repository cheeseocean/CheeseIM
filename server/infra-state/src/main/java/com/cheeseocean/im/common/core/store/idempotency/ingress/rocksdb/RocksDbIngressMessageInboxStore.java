package com.cheeseocean.im.common.core.store.idempotency.ingress.rocksdb;

import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxStore;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * all-in-one 模式 ingress inbox；整个批次在单进程内同步迁移。
 */
public class RocksDbIngressMessageInboxStore implements IngressMessageInboxStore {

    private final RocksDbSupport support;
    private final Duration ttl;
    private final long leaseMillis;

    public RocksDbIngressMessageInboxStore(Path dataDirectory,
                                           ObjectMapper objectMapper,
                                           IngressMessageInboxProperties properties) {
        support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory").resolve("ingress-inbox"),
                Objects.requireNonNull(objectMapper, "objectMapper"));
        Objects.requireNonNull(properties, "properties");
        ttl = Duration.ofSeconds(properties.normalizedTtlSeconds());
        leaseMillis = properties.normalizedLeaseMillis();
    }

    @Override
    public synchronized List<Claim> claimBatch(List<ClaimRequest> requests,
                                               String ownerToken,
                                               long nowMillis) {
        return requests.stream()
                .map(request -> claim(request, ownerToken, nowMillis))
                .toList();
    }

    @Override
    public synchronized Map<String, Long> bindSequences(List<SequenceBinding> bindings,
                                                        String ownerToken) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (SequenceBinding binding : bindings) {
            State state = requireOwned(binding.key(), ownerToken, "bind seq");
            long stableSeq = state.assignedSeq() > 0 ? state.assignedSeq() : binding.proposedSeq();
            if (stableSeq <= 0) {
                throw new IllegalArgumentException("Ingress inbox seq must be positive");
            }
            support.put(binding.key(), new State(
                    state.fingerprint(),
                    state.status(),
                    state.owner(),
                    state.leaseUntil(),
                    stableSeq), ttl);
            result.put(binding.key(), stableSeq);
        }
        return result;
    }

    @Override
    public synchronized void completeBatch(List<String> keys, String ownerToken) {
        for (String key : keys) {
            State state = requireOwned(key, ownerToken, "complete");
            support.put(key, new State(
                    state.fingerprint(),
                    "COMPLETED",
                    "",
                    0L,
                    state.assignedSeq()), ttl);
        }
    }

    @Override
    public synchronized void releaseBatch(List<String> keys, String ownerToken) {
        for (String key : keys) {
            State state = support.get(key, State.class);
            if (state == null
                    || !"PROCESSING".equals(state.status())
                    || !Objects.equals(ownerToken, state.owner())) {
                continue;
            }
            support.put(key, new State(
                    state.fingerprint(),
                    state.status(),
                    "",
                    0L,
                    state.assignedSeq()), ttl);
        }
    }

    private Claim claim(ClaimRequest request, String ownerToken, long nowMillis) {
        State state = support.get(request.key(), State.class);
        if (state == null) {
            State created = new State(
                    request.payloadFingerprint(),
                    "PROCESSING",
                    ownerToken,
                    nowMillis + leaseMillis,
                    0L);
            support.put(request.key(), created, ttl);
            return result(request.key(), ClaimStatus.ACQUIRED, created);
        }
        if (!Objects.equals(request.payloadFingerprint(), state.fingerprint())) {
            return result(request.key(), ClaimStatus.CONFLICT, state);
        }
        if ("COMPLETED".equals(state.status())) {
            support.put(request.key(), state, ttl);
            return result(request.key(), ClaimStatus.COMPLETED, state);
        }
        if (Objects.equals(ownerToken, state.owner()) || state.leaseUntil() <= nowMillis) {
            State acquired = new State(
                    state.fingerprint(),
                    "PROCESSING",
                    ownerToken,
                    nowMillis + leaseMillis,
                    state.assignedSeq());
            support.put(request.key(), acquired, ttl);
            return result(request.key(), ClaimStatus.ACQUIRED, acquired);
        }
        return result(request.key(), ClaimStatus.IN_PROGRESS, state);
    }

    private State requireOwned(String key, String ownerToken, String operation) {
        State state = support.get(key, State.class);
        if (state == null
                || !"PROCESSING".equals(state.status())
                || !Objects.equals(ownerToken, state.owner())) {
            throw new IllegalStateException("Ingress inbox cannot " + operation + " without active lease");
        }
        return state;
    }

    private static Claim result(String key, ClaimStatus status, State state) {
        return new Claim(key, status, state.assignedSeq(), state.leaseUntil());
    }

    private record State(String fingerprint,
                         String status,
                         String owner,
                         long leaseUntil,
                         long assignedSeq) {
    }
}
