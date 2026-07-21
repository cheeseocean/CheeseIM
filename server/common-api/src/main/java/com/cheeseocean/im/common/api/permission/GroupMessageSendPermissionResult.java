package com.cheeseocean.im.common.api.permission;

import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 群消息发送权限聚合结果。
 */
@Data
public class GroupMessageSendPermissionResult implements Serializable {

    private String groupId;
    private GroupTypeEnum groupType;
    /**
     * 权限校验读取群资料时观察到的成员关系版本，后续扩散必须原样使用。
     */
    private long membershipVersion;
    private List<GroupMessageSendPermissionDecision> decisions;

    /**
     * 查询指定发送者的结果；provider 未返回该用户时按拒绝处理。
     */
    public GroupMessageSendPermissionDecision decisionFor(String senderId) {
        if (decisions == null) {
            return null;
        }
        return decisions.stream()
                .filter(decision -> decision != null && java.util.Objects.equals(
                        senderId, decision.getSenderId()))
                .findFirst()
                .orElse(null);
    }
}
