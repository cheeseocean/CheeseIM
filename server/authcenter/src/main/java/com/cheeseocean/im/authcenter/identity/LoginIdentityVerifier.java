package com.cheeseocean.im.authcenter.identity;

import com.cheeseocean.im.common.api.auth.AuthenticationCommand;

/**
 * 登录身份源端口。
 *
 * <p>session 编排只依赖“已验证主体”，密码、OAuth 或业务系统 assertion
 * 的具体实现必须封装在该边界内。</p>
 */
public interface LoginIdentityVerifier {

    /**
     * 验证一次登录请求并返回可信主体；验证失败必须抛出异常且不得降级信任 userId。
     */
    VerifiedLoginIdentity verify(AuthenticationCommand command);
}
