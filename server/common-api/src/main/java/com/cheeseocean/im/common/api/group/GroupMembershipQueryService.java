package com.cheeseocean.im.common.api.group;

import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;

import java.util.List;
import java.util.Optional;

/**
 * 群成员/群资料查询 Dubbo 契约。
 *
 * <p>所有方法面向读路径；写路径见 {@code business} 群业务服务。
 */
public interface GroupMembershipQueryService {

    /**
     * 按会话 id 查询群成员列表。
     *
     * <p>会话 id 形如 {@code g:{groupId}}（见 {@code ConversationIdUtil.group}）。
     */
    List<String> queryConversationMembers(String conversationId);

    /**
     * 按群 id 查询群成员列表。
     */
    List<String> queryGroupMembers(String groupId);

    /**
     * 判断指定用户是否为群成员。
     */
    boolean isGroupMember(String groupId, String userId);

    /**
     * 按群 id 查询群资料。
     *
     * <p>HTTP 层只能经此契约读取群资料，禁止直接依赖 Mongo Repository。</p>
     */
    Optional<Group> queryGroup(String groupId);

    /**
     * 查询群类型（{@link GroupTypeEnum#NORMAL_GROUP} 写扩散 / {@link GroupTypeEnum#SUPER_GROUP} 读扩散）。
     *
     * <p>用于 postmaster 在 ingress 热路径决定群消息扩散模式：
     * <ul>
     *   <li>NORMAL_GROUP：postmaster 写扩散，按成员批量 publish N 个 keyed DeliveryEvent</li>
     *   <li>SUPER_GROUP：postmaster 仅持久化，不投递；客户端按 seq 同步拉取</li>
     *   <li>null：群不存在或查询异常，调用方需自降级（默认按 NORMAL_GROUP 处理或跳过）</li>
     * </ul>
     * 该方法应在 {@code business} 实现侧通过统一 CacheStore 加缓存，避免每条群消息都打 Mongo。
     */
    GroupTypeEnum queryGroupType(String groupId);
}
