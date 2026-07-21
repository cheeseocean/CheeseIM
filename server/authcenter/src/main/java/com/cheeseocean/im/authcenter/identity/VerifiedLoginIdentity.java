package com.cheeseocean.im.authcenter.identity;

/**
 * 经过可信身份源验证的登录主体。
 *
 * @param userId 上游身份断言中的稳定用户标识
 */
public record VerifiedLoginIdentity(String userId) {
}
