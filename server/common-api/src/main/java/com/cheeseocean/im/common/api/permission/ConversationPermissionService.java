package com.cheeseocean.im.common.api.permission;

/**
 * 会话访问权限校验的跨模块契约。
 *
 * <p>接口位于 common-api，调用方式可由 Dubbo 等基础设施承载，但领域命名不暴露 RPC 实现细节。</p>
 */
public interface ConversationPermissionService {

    /** 校验当前用户是否允许访问目标会话。 */
    PermissionCheckResult check(ConversationPermissionRequest request);
}
