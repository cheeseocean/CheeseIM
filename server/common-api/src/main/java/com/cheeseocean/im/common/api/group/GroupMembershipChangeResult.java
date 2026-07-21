package com.cheeseocean.im.common.api.group;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 群成员变更结果。
 */
@Data
public class GroupMembershipChangeResult implements Serializable {

    private String groupId;
    private long membershipVersion;
    private List<String> changedUserIds = new ArrayList<>();
}
