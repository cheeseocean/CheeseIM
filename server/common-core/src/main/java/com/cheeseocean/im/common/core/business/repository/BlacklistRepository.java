package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.Blacklist;

import java.util.List;

/**
 * 黑名单仓储抽象接口。
 *
 * <p>负责单向黑名单关系的读写。
 */
public interface BlacklistRepository {

    /**
     * 判断 targetUserId 是否已把 userId 拉黑。
     */
    boolean isBlocked(String userId, String targetUserId);

    /**
     * 将目标用户加入黑名单。
     */
    void blockUser(String userId, String targetUserId);

    /**
     * 将目标用户从黑名单移除。
     */
    void unblockUser(String userId, String targetUserId);

    /**
     * 查询某用户拉黑的全部 userId。
     */
    List<String> listBlockedUserIds(String userId);

    /**
     * 查询某用户的完整黑名单记录。
     */
    List<Blacklist> listBlacklist(String userId);
}
