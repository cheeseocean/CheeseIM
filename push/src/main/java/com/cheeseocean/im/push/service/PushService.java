package com.cheeseocean.im.push.service;

public interface PushService {

    boolean isPushAvailable(Integer platformId);

    PushStatistics getPushStatistics();

    class PushStatistics {
        private long totalPushCount;
        private long successPushCount;
        private long failedPushCount;

        public long getTotalPushCount() {
            return totalPushCount;
        }

        public void setTotalPushCount(long totalPushCount) {
            this.totalPushCount = totalPushCount;
        }

        public long getSuccessPushCount() {
            return successPushCount;
        }

        public void setSuccessPushCount(long successPushCount) {
            this.successPushCount = successPushCount;
        }

        public long getFailedPushCount() {
            return failedPushCount;
        }

        public void setFailedPushCount(long failedPushCount) {
            this.failedPushCount = failedPushCount;
        }

        public double getSuccessRate() {
            if (totalPushCount == 0) {
                return 0D;
            }
            return successPushCount * 100.0D / totalPushCount;
        }
    }
}
