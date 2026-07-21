package com.cheeseocean.im.common.api.permission;

import com.cheeseocean.im.common.api.enums.GroupSendPermissionCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 单个发送者的群消息权限结果。
 */
@Data
public class GroupMessageSendPermissionDecision implements Serializable {

    private String senderId;
    private int permissionCode;
    private long muteEndTime;

    public boolean isAllowed() {
        return permissionCode == GroupSendPermissionCode.ALLOWED.getCode();
    }

    public GroupSendPermissionCode permission() {
        return GroupSendPermissionCode.fromCode(permissionCode);
    }

    public static GroupMessageSendPermissionDecision of(String senderId,
                                                        GroupSendPermissionCode permission,
                                                        long muteEndTime) {
        GroupMessageSendPermissionDecision decision = new GroupMessageSendPermissionDecision();
        decision.setSenderId(senderId);
        decision.setPermissionCode(permission.getCode());
        decision.setMuteEndTime(muteEndTime);
        return decision;
    }
}
