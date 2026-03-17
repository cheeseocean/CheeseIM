package com.cheeseocean.im.push.service;

import java.util.Map;

public interface PushStatisticsService {

    void recordPushStatistics(String provider, Integer platformId, boolean success, long durationMs);

    PushService.PushStatistics getPushStatistics();

    RealtimePushStats getRealtimePushStats();

    Map<String, ProviderStats> getProviderStatistics();

    Map<Integer, PlatformStats> getPlatformStatistics();

    class RealtimePushStats {
        private long currentHourPushCount;
        private long currentMinutePushCount;
        private long currentSecondPushCount;
        private double currentSuccessRate;

        public long getCurrentHourPushCount() {
            return currentHourPushCount;
        }

        public void setCurrentHourPushCount(long currentHourPushCount) {
            this.currentHourPushCount = currentHourPushCount;
        }

        public long getCurrentMinutePushCount() {
            return currentMinutePushCount;
        }

        public void setCurrentMinutePushCount(long currentMinutePushCount) {
            this.currentMinutePushCount = currentMinutePushCount;
        }

        public long getCurrentSecondPushCount() {
            return currentSecondPushCount;
        }

        public void setCurrentSecondPushCount(long currentSecondPushCount) {
            this.currentSecondPushCount = currentSecondPushCount;
        }

        public double getCurrentSuccessRate() {
            return currentSuccessRate;
        }

        public void setCurrentSuccessRate(double currentSuccessRate) {
            this.currentSuccessRate = currentSuccessRate;
        }
    }

    class ProviderStats {
        private long totalCount;
        private long successCount;
        private long failedCount;

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(long successCount) {
            this.successCount = successCount;
        }

        public long getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(long failedCount) {
            this.failedCount = failedCount;
        }

        public double getSuccessRate() {
            if (totalCount == 0) {
                return 0D;
            }
            return successCount * 100.0D / totalCount;
        }
    }

    class PlatformStats {
        private Integer platformId;
        private String platformName;
        private long totalCount;
        private long successCount;
        private long failedCount;

        public Integer getPlatformId() {
            return platformId;
        }

        public void setPlatformId(Integer platformId) {
            this.platformId = platformId;
        }

        public String getPlatformName() {
            return platformName;
        }

        public void setPlatformName(String platformName) {
            this.platformName = platformName;
        }

        public long getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(long totalCount) {
            this.totalCount = totalCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(long successCount) {
            this.successCount = successCount;
        }

        public long getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(long failedCount) {
            this.failedCount = failedCount;
        }

        public double getSuccessRate() {
            if (totalCount == 0) {
                return 0D;
            }
            return successCount * 100.0D / totalCount;
        }
    }
}
