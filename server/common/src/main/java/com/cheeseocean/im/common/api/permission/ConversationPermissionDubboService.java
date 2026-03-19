package com.cheeseocean.im.common.api.permission;

import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;

public interface ConversationPermissionDubboService {

    PermissionCheckResult check(PermissionCheckRequest request);
}
