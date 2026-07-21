package com.cheeseocean.im.common.core.queue.dlt;

/**
 * DLT 非破坏性查询与受控重放端口。
 *
 * <p>redrive 只复制回原 topic，不删除或提交 DLT offset；原始 DLT 始终保留到 topic retention 到期。</p>
 */
public interface DltOperations {

    DltPage list(String sourceTopic, int partition, long afterOffset, int limit);

    DltRedriveResult redrive(DltRedriveCommand command);
}
