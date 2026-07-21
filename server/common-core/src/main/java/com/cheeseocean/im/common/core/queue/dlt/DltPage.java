package com.cheeseocean.im.common.core.queue.dlt;

import java.util.List;

/**
 * DLT offset 分页。
 */
public record DltPage(
        String sourceTopic,
        int partition,
        long beginningOffset,
        long endOffset,
        long nextAfterOffset,
        List<DltRecordSummary> records) {
}
