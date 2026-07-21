package com.cheeseocean.im.common.api.group;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 群成员稳定 keyset 分页结果。
 *
 * <p>游标使用 {@code (joinedVersion,userId,epochId)}，调用方必须原样回传 next 字段，
 * 禁止换成 offset。epochId 用于区分同一用户退群再入群产生的多个生命周期。</p>
 */
public class GroupMemberPage implements Serializable {

    private List<String> userIds = new ArrayList<>();
    private long nextJoinedVersion;
    private String nextUserId;
    private String nextEpochId;
    private boolean hasMore;

    public List<String> getUserIds() { return new ArrayList<>(userIds); }
    public void setUserIds(List<String> userIds) {
        this.userIds = userIds == null ? new ArrayList<>() : new ArrayList<>(userIds);
    }
    public long getNextJoinedVersion() { return nextJoinedVersion; }
    public void setNextJoinedVersion(long nextJoinedVersion) { this.nextJoinedVersion = nextJoinedVersion; }
    public String getNextUserId() { return nextUserId; }
    public void setNextUserId(String nextUserId) { this.nextUserId = nextUserId; }
    public String getNextEpochId() { return nextEpochId; }
    public void setNextEpochId(String nextEpochId) { this.nextEpochId = nextEpochId; }
    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
}
