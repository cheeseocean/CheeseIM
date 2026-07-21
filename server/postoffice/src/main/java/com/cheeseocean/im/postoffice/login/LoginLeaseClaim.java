package com.cheeseocean.im.postoffice.login;

import java.util.List;

/**
 * 全局登录 lease claim 结果。
 */
public record LoginLeaseClaim(Status status,
                              long generation,
                              List<LoginLease> evicted) {

    public enum Status {
        ACCEPTED,
        REJECTED_LIMIT
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }
}
