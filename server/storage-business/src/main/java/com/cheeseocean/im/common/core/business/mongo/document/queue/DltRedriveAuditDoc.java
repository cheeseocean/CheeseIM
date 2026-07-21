package com.cheeseocean.im.common.core.business.mongo.document.queue;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * DLT 重放审计。集合：{@code dlt_redrive_audit}。
 */
@Document("dlt_redrive_audit")
@CompoundIndexes({
        @CompoundIndex(name = "idx_dlt_record_created",
                def = "{'dltTopic': 1, 'partition': 1, 'dltOffset': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_dlt_status_lease",
                def = "{'status': 1, 'leaseUntil': 1}")
})
@Data
public class DltRedriveAuditDoc {

    @Id
    private String operationId;
    private String sourceTopic;
    private String dltTopic;
    private int partition;
    private long dltOffset;
    private String checksum;
    private String operatorId;
    private String reason;
    private int status;
    private String ownerToken;
    private long generation;
    private long leaseUntil;
    private long createdAt;
    private long completedAt;
    private long failedAt;
    private String error;
}
