package com.cheeseocean.im.common.api.friend;

import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;

import java.util.List;

/**
 * 好友关系领域服务。
 *
 * <p>服务接口直接返回领域对象，避免在 Dubbo 合约层引入展示型摘要对象。
 *
 * @author xxxcrel
 */
public interface FriendRelationService {

    /**
     * 查询当前用户的好友列表。
     */
    List<Friendship> listFriends(String userId);

    /**
     * 查询当前用户收到的待处理好友申请。
     */
    List<FriendRequest> listIncomingRequests(String userId);

    /**
     * 查询当前用户发出的待处理好友申请。
     */
    List<FriendRequest> listOutgoingRequests(String userId);

    /**
     * 发起或刷新一条好友申请。
     */
    FriendRequest sendFriendRequest(String userId, String friendUserId, String requestMessage);

    /**
     * 同意一条好友申请，并返回当前用户视角下的好友关系。
     */
    Friendship acceptFriendRequest(String userId, String friendUserId);

    /**
     * 拒绝一条好友申请，并返回更新后的申请记录。
     */
    FriendRequest rejectFriendRequest(String userId, String friendUserId);

    /**
     * 取消一条我发出的好友申请，并返回更新后的申请记录。
     */
    FriendRequest cancelFriendRequest(String userId, String friendUserId);

    /**
     * 判断双方是否已经建立好友关系。
     */
    boolean areAcceptedFriends(String userId, String friendUserId);

    /**
     * Returns true if targetUserId has blocked userId (i.e. userId is on targetUserId's blacklist).
     * Used in sendMessage to decide whether to drop the message before delivery.
     */
    boolean isBlocked(String userId, String targetUserId);

    void blockUser(String userId, String targetUserId);

    void unblockUser(String userId, String targetUserId);

    /**
     * 查询当前用户的黑名单用户 ID 列表。
     */
    List<String> listBlockedUserIds(String userId);
}
