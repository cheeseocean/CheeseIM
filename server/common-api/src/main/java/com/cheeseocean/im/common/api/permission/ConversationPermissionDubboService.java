package com.cheeseocean.im.common.api.permission;

/**
 * 会话读取权限校验接口。
 *
 * @author xxxcrel
 */
public interface ConversationPermissionDubboService {

    /**
     * 校验当前读取请求是否允许访问目标会话。
     */
    PermissionCheckResult check(ConversationPermissionRequest request);
}
