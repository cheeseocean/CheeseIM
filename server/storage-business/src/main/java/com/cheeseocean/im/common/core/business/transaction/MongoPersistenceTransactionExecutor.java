package com.cheeseocean.im.common.core.business.transaction;

import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * MongoDB 事务执行器。
 *
 * <p>all-in-one 默认不要求单机 Mongo 以副本集启动，因此直接执行操作；
 * cluster 默认通过 {@link TransactionTemplate} 包裹操作，保证多文档写入原子提交。
 */
public class MongoPersistenceTransactionExecutor implements PersistenceTransactionExecutor {

    private final TransactionTemplate transactionTemplate;
    private final boolean             transactionsEnabled;

    public MongoPersistenceTransactionExecutor(TransactionTemplate transactionTemplate,
                                               boolean transactionsEnabled) {
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.transactionsEnabled = transactionsEnabled;
    }

    @Override
    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (!transactionsEnabled) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
