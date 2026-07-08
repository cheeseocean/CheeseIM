package com.cheeseocean.im.common.api.session;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户级安全状态。
 *
 * <p>tokenVersion 是登录态的用户级失效开关：踢全端、封禁等安全操作 bump 后，
 * 旧 access token / WS ticket / session 中携带的版本都会低于当前版本，从而被拒绝。
 */
@Data
public class UserSecurityState implements Serializable {

    private String userId;
    private long tokenVersion = 1L;
    private boolean banned;
    private long updatedAt;
}
