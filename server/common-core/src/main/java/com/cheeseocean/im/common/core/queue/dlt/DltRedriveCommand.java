package com.cheeseocean.im.common.core.queue.dlt;

/**
 * 单条 DLT 受控重放命令。
 *
 * <p>expectedChecksum 防止列表查询后 offset 内容发生误认；operationId 是调用方生成的幂等操作 ID。</p>
 */
public record DltRedriveCommand(
        String operationId,
        String sourceTopic,
        int partition,
        long offset,
        String expectedChecksum,
        String operatorId,
        String reason) {
}
