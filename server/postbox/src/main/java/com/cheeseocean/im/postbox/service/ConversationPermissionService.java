package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;

/**
 * 本地会话权限查询抽象。
 *
 * @author xxxcrel
 */
public interface ConversationPermissionService {

    PermissionCheckResult check(ConversationPermissionRequest request);
}
