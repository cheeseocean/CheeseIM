package com.cheeseocean.im.common.api.business.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 群成员关系的不可变生命周期区间。
 *
 * <p>区间采用 {@code [joinedVersion,leftVersionExclusive)}。同一用户退群再入群会产生新的 epoch，
 * 旧 epoch 只关闭不覆盖，保证历史扩散任务能够按版本重放。</p>
 */
@Data
public class GroupMemberEpoch implements Serializable {

    private String epochId;
    private String groupId;
    private String userId;
    private long joinedVersion;
    private long leftVersionExclusive;
}
