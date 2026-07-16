package com.cheeseocean.im.common.core.store.delivery.rocksdb;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/** 单机开发模式的设备送达高水位实现。 */
public class RocksDbDeliveryStateStore implements DeliveryStateStore {
    private final RocksDbSupport support;

    public RocksDbDeliveryStateStore(Path path, ObjectMapper objectMapper) {
        this.support = new RocksDbSupport(path, objectMapper);
    }

    @Override
    public synchronized AdvanceResult advance(String userId, String deviceId, String conversationId, long requestedSeq) {
        String key = RedisKeys.deviceDeliveredSeq(userId, deviceId, conversationId);
        String stored = support.get(key, String.class);
        long current = stored == null ? 0L : Long.parseLong(stored);
        if (requestedSeq <= current) return new AdvanceResult(current, false);
        support.put(key, String.valueOf(requestedSeq), null);
        return new AdvanceResult(requestedSeq, true);
    }
}
