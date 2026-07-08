package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.session.UserSecurityState;

import java.util.Optional;

/**
 * 用户安全状态持久化仓储。
 */
public interface UserSecurityStateRepository {

    Optional<UserSecurityState> findByUserId(String userId);

    UserSecurityState bumpTokenVersion(String userId);

    UserSecurityState setBanned(String userId, boolean banned);
}
