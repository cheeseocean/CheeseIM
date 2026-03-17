package com.cheeseocean.im.push.service.impl;

import com.cheeseocean.im.push.service.PushService;
import com.cheeseocean.im.push.service.PushStatisticsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class InMemoryPushStatisticsService implements PushStatisticsService {

    private final AtomicLong totalPushCount = new AtomicLong();
    private final AtomicLong successPushCount = new AtomicLong();
    private final AtomicLong failedPushCount = new AtomicLong();
    private final Map<String, ProviderStatsBucket> providerStats = new ConcurrentHashMap<>();
    private final Map<Integer, PlatformStatsBucket> platformStats = new ConcurrentHashMap<>();
    private volatile Instant lastRecordedAt;

    @Override
    public void recordPushStatistics(String provider, Integer platformId, boolean success, long durationMs) {
        totalPushCount.incrementAndGet();
        if (success) {
            successPushCount.incrementAndGet();
        } else {
            failedPushCount.incrementAndGet();
        }
        providerStats.computeIfAbsent(provider == null ? "unknown" : provider, key -> new ProviderStatsBucket())
                .record(success);
        platformStats.computeIfAbsent(platformId == null ? 0 : platformId, this::newPlatformBucket)
                .record(success);
        lastRecordedAt = Instant.now();
    }

    @Override
    public PushService.PushStatistics getPushStatistics() {
        PushService.PushStatistics stats = new PushService.PushStatistics();
        stats.setTotalPushCount(totalPushCount.get());
        stats.setSuccessPushCount(successPushCount.get());
        stats.setFailedPushCount(failedPushCount.get());
        return stats;
    }

    @Override
    public RealtimePushStats getRealtimePushStats() {
        RealtimePushStats stats = new RealtimePushStats();
        long total = totalPushCount.get();
        if (lastRecordedAt != null && lastRecordedAt.isAfter(Instant.now().minus(1, ChronoUnit.HOURS))) {
            stats.setCurrentHourPushCount(total);
        }
        if (lastRecordedAt != null && lastRecordedAt.isAfter(Instant.now().minus(1, ChronoUnit.MINUTES))) {
            stats.setCurrentMinutePushCount(total);
        }
        if (lastRecordedAt != null && lastRecordedAt.isAfter(Instant.now().minus(1, ChronoUnit.SECONDS))) {
            stats.setCurrentSecondPushCount(total);
        }
        stats.setCurrentSuccessRate(getPushStatistics().getSuccessRate());
        return stats;
    }

    @Override
    public Map<String, ProviderStats> getProviderStatistics() {
        Map<String, ProviderStats> snapshot = new ConcurrentHashMap<>();
        providerStats.forEach((key, value) -> snapshot.put(key, value.toStats()));
        return snapshot;
    }

    @Override
    public Map<Integer, PlatformStats> getPlatformStatistics() {
        Map<Integer, PlatformStats> snapshot = new ConcurrentHashMap<>();
        platformStats.forEach((key, value) -> snapshot.put(key, value.toStats()));
        return snapshot;
    }

    private PlatformStatsBucket newPlatformBucket(Integer platformId) {
        return new PlatformStatsBucket(platformId, platformName(platformId));
    }

    private String platformName(Integer platformId) {
        return switch (platformId == null ? 0 : platformId) {
            case 1 -> "iOS";
            case 2 -> "Android";
            case 3 -> "Web";
            case 4 -> "Windows";
            case 5 -> "Mac";
            default -> "Unknown";
        };
    }

    private static final class ProviderStatsBucket {
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failedCount = new AtomicLong();

        private void record(boolean success) {
            totalCount.incrementAndGet();
            if (success) {
                successCount.incrementAndGet();
            } else {
                failedCount.incrementAndGet();
            }
        }

        private ProviderStats toStats() {
            ProviderStats stats = new ProviderStats();
            stats.setTotalCount(totalCount.get());
            stats.setSuccessCount(successCount.get());
            stats.setFailedCount(failedCount.get());
            return stats;
        }
    }

    private static final class PlatformStatsBucket {
        private final Integer platformId;
        private final String platformName;
        private final AtomicLong totalCount = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failedCount = new AtomicLong();

        private PlatformStatsBucket(Integer platformId, String platformName) {
            this.platformId = platformId;
            this.platformName = platformName;
        }

        private void record(boolean success) {
            totalCount.incrementAndGet();
            if (success) {
                successCount.incrementAndGet();
            } else {
                failedCount.incrementAndGet();
            }
        }

        private PlatformStats toStats() {
            PlatformStats stats = new PlatformStats();
            stats.setPlatformId(platformId);
            stats.setPlatformName(platformName);
            stats.setTotalCount(totalCount.get());
            stats.setSuccessCount(successCount.get());
            stats.setFailedCount(failedCount.get());
            return stats;
        }
    }
}
