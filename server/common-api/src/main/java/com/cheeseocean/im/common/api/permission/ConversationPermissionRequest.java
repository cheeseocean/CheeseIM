package com.cheeseocean.im.common.api.permission;

import lombok.Data;

/**
 * 会话权限校验请求。
 *
 * @author xxxcrel
 */
@Data
public class ConversationPermissionRequest {

    private String tenantId;
    private String userId;
    private String conversationId;
}
