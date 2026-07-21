package com.cheeseocean.im.common.core.queue.dlt;

/**
 * DLT 记录摘要；默认不暴露原始 payload，避免运维查询泄漏消息内容。
 */
public record DltRecordSummary(
        String sourceTopic,
        String dltTopic,
        int partition,
        long offset,
        long timestamp,
        String keyFingerprint,
        int payloadBytes,
        String checksum,
        String exceptionClass,
        String exceptionMessage) {
}
