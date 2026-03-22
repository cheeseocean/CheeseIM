package com.cheeseocean.im.common.api.permission;

import com.cheeseocean.im.common.core.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;

public interface ConversationPermissionDubboService {

    PermissionCheckResult check(PermissionCheckRequest request);
}
