package com.cheeseocean.im.postoffice.login;

import com.cheeseocean.im.postoffice.connection.MultiLoginStrategy;

import java.util.List;
import java.util.Set;

/**
 * 跨节点登录所有权状态机。
 *
 * <p>生产实现必须以单用户同槽 Lua 原子完成 claim，Redis 故障时不得降级为节点本地放行。</p>
 */
public interface LoginLeaseStore {

    LoginLeaseClaim claim(LoginLease requested,
                          MultiLoginStrategy strategy,
                          int maxConnections);

    /**
     * 批量续租并返回已被 fencing 或不存在的 connectionId。
     */
    Set<String> renewBatch(List<LoginLeaseRenewal> renewals);

    void release(LoginLeaseRenewal renewal);

    void releaseBatch(List<LoginLeaseRenewal> renewals);
}
