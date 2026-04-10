package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.FriendRequest;

import java.util.List;
import java.util.Map;

/**
 * 好友申请仓储抽象接口。
 *
 * <p>每对发起方和接收方之间最多保留一条当前申请记录。
 */
public interface FriendRequestRepository {

    /**
     * 批量保存好友申请。
     */
    void saveAll(List<FriendRequest> requests);

    /**
     * 按字段更新单条好友申请。
     */
    void updateFields(String fromUserId, String toUserId, Map<String, Object> fields);

    /**
     * 保存完整好友申请对象。
     */
    void update(FriendRequest request);

    /**
     * 删除指定方向的一条好友申请。
     */
    void delete(String fromUserId, String toUserId);

    /**
     * 查询指定方向的一条好友申请。
     */
    FriendRequest find(String fromUserId, String toUserId);

    /**
     * 查询两名用户之间双向存在的申请记录。
     */
    List<FriendRequest> findBothDirections(String userA, String userB);

    /**
     * 分页查询某用户收到的好友申请。
     */
    List<FriendRequest> findIncoming(String toUserId, List<Integer> handleResults, int limit, int offset);

    /**
     * 分页查询某用户发出的好友申请。
     */
    List<FriendRequest> findOutgoing(String fromUserId, List<Integer> handleResults, int limit, int offset);

    /**
     * 统计某用户未处理的好友申请数量。
     */
    long countUnhandled(String toUserId, long afterTs);
}
