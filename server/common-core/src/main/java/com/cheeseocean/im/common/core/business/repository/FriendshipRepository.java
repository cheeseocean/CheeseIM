package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.Friendship;

import java.util.List;
import java.util.Map;

/**
 * 好友关系仓储抽象接口。
 *
 * <p>每条记录表示 ownerUserId 视角下的一条好友关系。
 */
public interface FriendshipRepository {

    /**
     * 批量保存好友关系。
     */
    void saveAll(List<Friendship> friendships);

    /**
     * 删除一侧用户视角下的多条好友关系。
     */
    void delete(String ownerUserId, List<String> friendUserIds);

    /**
     * 更新单条好友关系的部分字段。
     */
    void updateFields(String ownerUserId, String friendUserId, Map<String, Object> fields);

    /**
     * 批量更新同一 ownerUserId 下多条好友关系的部分字段。
     */
    void updateBatchFields(String ownerUserId, List<String> friendUserIds, Map<String, Object> fields);

    /**
     * 查询单条好友关系。
     */
    Friendship find(String ownerUserId, String friendUserId);

    /**
     * 查询 ownerUserId 视角下指定好友集合的关系记录。
     */
    List<Friendship> findFriends(String ownerUserId, List<String> friendUserIds);

    /**
     * 反向查询：找出把指定用户当作好友的一组记录。
     */
    List<Friendship> findReverseFriends(String friendUserId, List<String> ownerUserIds);

    /**
     * 分页查询某用户的好友列表。
     */
    List<Friendship> findOwnerFriends(String ownerUserId, int limit, int offset);

    /**
     * 查询某用户的好友 userId 列表。
     */
    List<String> findOwnerFriendUserIds(String ownerUserId, int limit);

    /**
     * 查询把某个用户加为好友的所有 ownerUserId。
     */
    List<String> findOwnersByFriendUserId(String friendUserId);
}
