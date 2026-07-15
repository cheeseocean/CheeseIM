package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 默认会话权限实现。
 *
 * <p>当前 CheeseBox 查询链路先走最小放行策略，后续再接真实权限服务。
 *
 * @author xxxcrel
 */
@DubboService
public class DefaultConversationPermissionService implements ConversationPermissionDubboService {

    @Override
    public PermissionCheckResult check(ConversationPermissionRequest request) {
        return PermissionCheckResult.allow();
    }
}
