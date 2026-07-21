package com.cheeseocean.im.postoffice.login;

/**
 * 待批量续租的本地连接身份。
 */
public record LoginLeaseRenewal(String tenantId,
                                String userId,
                                String connectionId,
                                long generation) {
}
