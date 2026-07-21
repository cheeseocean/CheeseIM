package com.cheeseocean.im.common.core.business.mongo.document.group;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * 群扩散分页任务进度。集合：{@code group_fanout_job}。
 */
@Document("group_fanout_job")
@CompoundIndex(name = "idx_fanout_status_lease", def = "{'status': 1, 'leaseUntil': 1}")
@Data
public class GroupFanoutJobDoc {

    @Id
    private String jobId;
    private int status;
    private String ownerToken;
    private long generation;
    private long leaseUntil;
    private long membershipVersion;
    private long joinedVersion;
    private String userId;
    private String epochId;
    private long completedAt;
    /**
     * 完成记录在 Kafka retention 之后才允许清理，防止合法重放失去幂等终态。
     */
    @Indexed(expireAfter = "0s")
    private Date expireAt;
}
