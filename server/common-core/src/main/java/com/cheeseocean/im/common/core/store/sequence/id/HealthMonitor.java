package com.cheeseocean.im.common.core.store.sequence.id;

/**
 * 号段分配主路径（Redis）的健康状态接口
 * <p>
 * 通过此接口抽象健康检测行为，便于在无 Redis 环境下注入空实现。
 */
interface HealthMonitor {

    /**
     * 主路径当前是否可用
     */
    boolean isAvailable();

    /**
     * 分配失败时主动标记主路径不可用，触发降级
     */
    void markUnavailable();

    /**
     * 停止后台健康检测线程
     */
    void shutdown();

    /**
     * 返回一个始终标记为不可用的空实现
     * 用于无 Redis 环境下始终走 RocksDB 路径
     */
    static HealthMonitor alwaysDown() {
        return new HealthMonitor() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public void markUnavailable() {
                // 空实现
            }

            @Override
            public void shutdown() {
                // 空实现
            }
        };
    }
}
