package com.cheeseocean.im.common.api.session;

/**
 * 长连接接入层使用的认证服务。
 *
 * <p>负责消费 ws-ticket 并校验当前 session 状态，供 TCP/WS 网关在认证首包阶段调用。
 *
 * @author xxxcrel
 */
public interface ConnectionAuthService {

    /**
     * 校验并消费 ws-ticket，返回可绑定到连接上的会话主体。
     */
    SessionPrincipal authenticateWsTicket(String ticket);
}
