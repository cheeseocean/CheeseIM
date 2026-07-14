package com.cheeseocean.im.common.core.business.transaction;

/**
 * 持久化操作事务边界。
 *
 * <p>业务模块只声明原子性需求，不直接依赖 Spring 或 MongoDB 的事务 API，
 * 以便单机联调可安全降级，而集群部署仍能启用数据库事务。
 */
@FunctionalInterface
public interface PersistenceTransactionExecutor {

    /**
     * 在当前部署模式允许时，以持久化事务执行操作。
     *
     * @param action 需要原子提交的持久化操作
     */
    void execute(Runnable action);
}
