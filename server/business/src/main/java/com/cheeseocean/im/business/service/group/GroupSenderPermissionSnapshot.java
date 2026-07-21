package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import lombok.Data;

/**
 * 群发送权限的短期缓存快照。
 *
 * <p>同时缓存允许与拒绝结果，防止不存在群/非成员请求形成 Mongo 穿透。</p>
 */
@Data
public class GroupSenderPermissionSnapshot {

    private GroupTypeEnum groupType;
    private long membershipVersion;
    private int permissionCode;
    private long muteEndTime;
}
