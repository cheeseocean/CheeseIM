package com.cheeseocean.im.common.core.store.rocksdb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ExpiringValue<T>(T value, long expiresAtEpochMillis) {

    public ExpiringValue {
        Objects.requireNonNull(value, "value");
    }

    public static <T> ExpiringValue<T> of(T value, Duration ttl, Instant now) {
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(now, "now");
        return new ExpiringValue<>(value, now.plus(ttl).toEpochMilli());
    }

    public static <T> ExpiringValue<T> of(T value, Duration ttl, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return of(value, ttl, clock.instant());
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return expiresAtEpochMillis <= now.toEpochMilli();
    }

    public boolean isExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return isExpired(clock.instant());
    }
}
