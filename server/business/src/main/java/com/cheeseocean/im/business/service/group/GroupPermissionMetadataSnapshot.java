package com.cheeseocean.im.business.service.group;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import lombok.Data;

/**
 * 群发送权限所需的轻量元数据缓存。
 */
@Data
public class GroupPermissionMetadataSnapshot {

    private boolean exists;
    private GroupTypeEnum groupType;
    private int statusCode;
    private long membershipVersion;
}
