package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.GroupMemberEpoch;

import java.util.List;

/**
 * 群成员历史生命周期仓储。
 *
 * <p>当前成员资料仍由 {@link GroupMemberRepository} 负责；本仓储仅服务版本化成员快照，
 * 防止扩散读模型与群资料写模型互相污染。</p>
 */
public interface GroupMemberEpochRepository {

    /**
     * 幂等写入版本 1 的基线 epoch；epochId 必须由调用方稳定生成。
     */
    void saveBaseline(List<GroupMemberEpoch> epochs);

    /**
     * 为新成员打开生命周期区间。
     */
    void openAll(List<GroupMemberEpoch> epochs);

    /**
     * 关闭用户当前仍活跃的生命周期区间。
     */
    void closeAll(String groupId, List<String> userIds, long leftVersionExclusive);

    /**
     * 按版本快照和三元 keyset 游标读取，最多返回 {@code limit + 1} 条。
     */
    List<GroupMemberEpoch> findPage(String groupId,
                                    long snapshotVersion,
                                    long afterJoinedVersion,
                                    String afterUserId,
                                    String afterEpochId,
                                    int limit);
}
