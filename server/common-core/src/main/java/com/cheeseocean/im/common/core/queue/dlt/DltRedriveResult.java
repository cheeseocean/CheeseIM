package com.cheeseocean.im.common.core.queue.dlt;

import com.cheeseocean.im.common.api.enums.DltRedriveStatus;

/**
 * DLT 重放结果。
 */
public record DltRedriveResult(
        String operationId,
        String sourceTopic,
        int partition,
        long dltOffset,
        String checksum,
        DltRedriveStatus status) {
}
