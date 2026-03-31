package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.Blacklist;
import com.cheeseocean.im.common.core.business.domain.FriendRequest;

import java.util.List;
import java.util.Optional;

/**
 * 好友聚合仓储抽象接口。
 *
 * <p>将好友关系（Friendship）、好友申请（FriendRequest）、
 * 黑名单（Blacklist）三个关联度高的子域合并为一个聚合仓储，
 * 底层实现负责 MongoDB 读写和 Redis 缓存维护。
 */
public interface FriendRepository {

    // ── 好友关系 ──────────────────────────────────────────────────────────────

    /** 判断两用户是否已是好友 */
    boolean areAcceptedFriends(String userId, String friendUserId);

    /** 获取用户的全部好友 ID 列表 */
    List<String> listFriendIds(String userId);

    /**
     * 接受好友申请，双向建立好友关系。
     * 同时将申请记录状态更新为 ACCEPTED。
     */
    void acceptFriendPair(String userId, String friendUserId);

    // ── 好友申请 ──────────────────────────────────────────────────────────────

    /** 获取用户收到的待处理申请列表（按最近更新倒序） */
    List<FriendRequest> listIncomingPendingRequests(String userId);

    /** 获取用户发出的待处理申请列表（按最近更新倒序） */
    List<FriendRequest> listOutgoingPendingRequests(String userId);

    /** 保存一条待处理的好友申请 */
    void savePendingRequest(String fromUserId, String toUserId, String reqMsg);

    /** 检查是否有发往对方的待处理申请 */
    boolean hasOutgoingPendingRequest(String userId, String friendUserId);

    /** 检查是否有来自对方的待处理申请 */
    boolean hasIncomingPendingRequest(String userId, String friendUserId);

    /** 查询两人之间待处理的申请（不存在时返回 empty） */
    Optional<FriendRequest> findPendingRequest(String fromUserId, String toUserId);

    /** 拒绝好友申请 */
    void rejectRequest(String fromUserId, String toUserId);

    /** 撤销好友申请（申请方主动取消） */
    void cancelRequest(String fromUserId, String toUserId);

    // ── 黑名单 ────────────────────────────────────────────────────────────────

    /** 检查 targetUserId 是否将 userId 加入了黑名单 */
    boolean isBlocked(String userId, String targetUserId);

    /** 将 targetUserId 加入 userId 的黑名单 */
    void blockUser(String userId, String targetUserId);

    /** 将 targetUserId 从 userId 的黑名单中移除 */
    void unblockUser(String userId, String targetUserId);

    /** 获取 userId 黑名单中的全部用户 ID */
    List<String> listBlockedUserIds(String userId);

    /** 获取黑名单详情列表（含拉黑来源、时间等） */
    List<Blacklist> listBlacklist(String userId);
}
