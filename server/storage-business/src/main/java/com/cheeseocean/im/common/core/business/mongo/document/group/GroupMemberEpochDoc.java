package com.cheeseocean.im.common.core.business.mongo.document.group;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 群成员生命周期持久化文档。集合：{@code group_member_epoch}。
 *
 * <p>该集合是历史快照事实表，退群只关闭区间，禁止物理删除。</p>
 */
@Document("group_member_epoch")
@CompoundIndexes({
        @CompoundIndex(name = "uk_group_user_joined",
                def = "{'groupId': 1, 'userId': 1, 'joinedVersion': 1}", unique = true),
        @CompoundIndex(name = "uk_group_user_epoch_end",
                def = "{'groupId': 1, 'userId': 1, 'leftVersionExclusive': 1}", unique = true),
        @CompoundIndex(name = "idx_group_snapshot_page",
                def = "{'groupId': 1, 'joinedVersion': 1, 'userId': 1, 'epochId': 1, 'leftVersionExclusive': 1}")
})
@Data
public class GroupMemberEpochDoc {

    @Id
    private String epochId;
    private String groupId;
    private String userId;
    private long joinedVersion;
    private long leftVersionExclusive;
}
