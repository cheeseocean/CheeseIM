package com.cheeseocean.im.business.service.conversation;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.template.QuickConfig;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.enums.SessionType;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * 会话统一服务实现。
 *
 * <p>统一承载会话查询、会话创建和会话配置更新，
 * 并在内部完成会话相关 JetCache 的读穿透与批量失效。
 */
@Service
@DubboService
public class ConversationServiceImpl implements ConversationService {

    private static final int REMOTE_EXPIRE_SECONDS = 60 * 60 * 12;
    private static final int LOCAL_EXPIRE_SECONDS = 60 * 5;
    private static final int LOCAL_LIMIT = 1_000;

    private final UserConversationRepository stateRepository;
    private final CacheManager cacheManager;

    /**
     * 单条会话配置缓存。
     * key: ownerUserId:conversationId
     */
    private Cache<String, UserConversation> conversationDetailCache;

    /**
     * 用户会话 ID 列表缓存。
     */
    private Cache<String, List<String>> conversationIdsCache;

    /**
     * 用户会话 ID 列表 hash 缓存。
     */
    private Cache<String, Long> conversationIdsHashCache;

    /**
     * 用户置顶会话 ID 列表缓存。
     */
    private Cache<String, List<String>> pinnedConversationIdsCache;

    /**
     * 用户免提醒会话 ID 列表缓存。
     */
    private Cache<String, List<String>> notNotifyConversationIdsCache;

    /**
     * 会话下不接收消息的用户 ID 列表缓存。
     */
    private Cache<String, List<String>> conversationNotReceiveUserIdsCache;

    public ConversationServiceImpl(UserConversationRepository stateRepository,
                                   CacheManager cacheManager) {
        this.stateRepository = stateRepository;
        this.cacheManager = cacheManager;
        this.conversationDetailCache = createCache("im:conv:detail:");
        this.conversationIdsCache = createCache("im:conv:ids:");
        this.conversationIdsHashCache = createCache("im:conv:ids_hash:");
        this.pinnedConversationIdsCache = createCache("im:conv:pinned:");
        this.notNotifyConversationIdsCache = createCache("im:conv:not_notify:");
        this.conversationNotReceiveUserIdsCache = createCache("im:conv:not_receive:");
    }

    @Override
    /**
     * 单条会话读取走 detail cache 读穿透，避免频繁击穿仓储层。
     */
    public UserConversation getConversation(String ownerUserId, String conversationId) {
        if (isBlank(ownerUserId) || isBlank(conversationId)) {
            return null;
        }
        return copy(conversationDetailCache.computeIfAbsent(
                detailKey(ownerUserId, conversationId),
                key -> stateRepository.findOne(ownerUserId, conversationId)
        ));
    }

    @Override
    /**
     * 批量会话读取优先命中缓存，未命中的部分再批量回源并回填缓存。
     */
    public List<UserConversation> getConversations(String ownerUserId, List<String> conversationIds) {
        if (isBlank(ownerUserId) || conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }
        List<String> dedupedConversationIds = dedupeConversationIds(conversationIds);
        if (dedupedConversationIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> keyToConversationId = new LinkedHashMap<>();
        for (String conversationId : dedupedConversationIds) {
            keyToConversationId.put(detailKey(ownerUserId, conversationId), conversationId);
        }
        Map<String, UserConversation> cached = conversationDetailCache.getAll(keyToConversationId.keySet());
        Map<String, UserConversation> resolved = new HashMap<>();
        if (cached != null) {
            resolved.putAll(cached);
        }

        List<String> misses = keyToConversationId.keySet().stream()
                .filter(key -> !resolved.containsKey(key))
                .map(keyToConversationId::get)
                .toList();
        if (!misses.isEmpty()) {
            List<UserConversation> loaded = stateRepository.findByIds(ownerUserId, misses);
            Map<String, UserConversation> loadedMap = loaded.stream()
                    .collect(Collectors.toMap(
                            conversation -> detailKey(ownerUserId, conversation.getConversationId()),
                            conversation -> conversation,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            if (!loadedMap.isEmpty()) {
                conversationDetailCache.putAll(loadedMap);
                resolved.putAll(loadedMap);
            }
        }

        return dedupedConversationIds.stream()
                .map(conversationId -> resolved.get(detailKey(ownerUserId, conversationId)))
                .filter(conversation -> conversation != null)
                .map(this::copy)
                .toList();
    }

    @Override
    /**
     * 全量会话查询复用“会话 ID 列表 + 单条详情”两级缓存，避免缓存整份大列表对象。
     */
    public List<UserConversation> getAllConversations(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return List.of();
        }
        List<String> conversationIds = getConversationIds(ownerUserId);
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        return getConversations(ownerUserId, conversationIds).stream()
                .sorted(Comparator.comparingLong(UserConversation::getUpdatedAt).reversed())
                .toList();
    }

    @Override
    /**
     * 会话 ID 列表是会话列表同步的基础缓存项。
     */
    public List<String> getConversationIds(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return List.of();
        }
        List<String> cached = conversationIdsCache.get(ownerUserId);
        if (cached != null) {
            return cached;
        }
        List<String> loaded = stateRepository.findConversationIds(ownerUserId);
        if (loaded == null) {
            loaded = List.of();
        }
        conversationIdsCache.put(ownerUserId, loaded);
        return loaded;
    }

    @Override
    /**
     * hash 由排序后的会话 ID 列表计算，保证同一集合在不同读取顺序下结果一致。
     */
    public long getConversationIdsHash(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return 0L;
        }
        Long cached = conversationIdsHashCache.get(ownerUserId);
        if (cached != null) {
            return cached;
        }
        List<String> ids = new ArrayList<>(getConversationIds(ownerUserId));
        ids.sort(String::compareTo);
        long hash = (long) ids.hashCode() & 0xFFFFFFFFL;
        conversationIdsHashCache.put(ownerUserId, hash);
        return hash;
    }

    @Override
    /**
     * 免提醒会话 ID 列表单独缓存，避免会话列表和消息链路重复扫描用户会话表。
     */
    public List<String> getNotNotifyConversationIds(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return List.of();
        }
        List<String> cached = notNotifyConversationIdsCache.get(ownerUserId);
        if (cached != null) {
            return cached;
        }
        List<String> loaded = stateRepository.findNotNotifyConversationIds(ownerUserId);
        if (loaded == null) {
            loaded = List.of();
        }
        notNotifyConversationIdsCache.put(ownerUserId, loaded);
        return loaded;
    }

    @Override
    /**
     * 置顶会话 ID 列表单独缓存，便于客户端快速恢复置顶排序。
     */
    public List<String> getPinnedConversationIds(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return List.of();
        }
        List<String> cached = pinnedConversationIdsCache.get(ownerUserId);
        if (cached != null) {
            return cached;
        }
        List<String> loaded = stateRepository.findPinnedConversationIds(ownerUserId);
        if (loaded == null) {
            loaded = List.of();
        }
        pinnedConversationIdsCache.put(ownerUserId, loaded);
        return loaded;
    }

    @Override
    /**
     * 接收选项直接复用会话详情缓存，不单独维护字段级缓存副本。
     */
    public int getReceiveOption(String ownerUserId, String conversationId) {
        UserConversation conversation = getConversation(ownerUserId, conversationId);
        return conversation == null ? ReceiveOption.RECEIVE.getCode() : conversation.getReceiveOpt();
    }

    @Override
    /**
     * 离线推送过滤依赖会话级 not-receive 用户集合缓存，适合高频复用。
     */
    public List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds) {
        if (isBlank(conversationId) || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return List.of();
        }
        List<String> notReceiveUserIds = conversationNotReceiveUserIdsCache.computeIfAbsent(
                conversationId,
                stateRepository::findAllNotReceiveUserIds
        );
        Set<String> notReceiveSet = new HashSet<>(notReceiveUserIds);
        return candidateUserIds.stream()
                .filter(userId -> !notReceiveSet.contains(userId))
                .toList();
    }

    @Override
    @Transactional
    /**
     * 单聊创建时为双方初始化用户维度会话记录；通知会话只为接收方创建。
     */
    public void createSingleChatConversation(String senderId, String recvId, String conversationId, int conversationType) {
        ConversationCacheEvictPlan plan = new ConversationCacheEvictPlan();
        if (conversationType == SessionType.SINGLE.getCode()) {
            createParticipantConversation(buildExplicitState(senderId, conversationId, conversationType, recvId), plan);
            createParticipantConversation(buildExplicitState(recvId, conversationId, conversationType, senderId), plan);
        } else {
            createParticipantConversation(buildExplicitState(recvId, conversationId, conversationType, senderId), plan);
        }
        evictAfterCommit(plan);
    }

    @Override
    @Transactional
    /**
     * 群会话创建只为缺失会话记录的成员补建用户会话。
     * 已存在记录的成员保持原个性化配置不变。
     */
    public void createGroupChatConversations(String groupId, String conversationId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<String> dedupedUserIds = userIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
        if (dedupedUserIds.isEmpty()) {
            return;
        }
        List<String> existingUserIds = stateRepository.findExistingOwnerUserIds(dedupedUserIds, conversationId);
        Set<String> existingUserIdSet = new HashSet<>(existingUserIds);
        ConversationCacheEvictPlan plan = new ConversationCacheEvictPlan();
        for (String userId : dedupedUserIds) {
            if (existingUserIdSet.contains(userId)) {
                continue;
            }
            createParticipantConversation(
                    buildExplicitState(userId, conversationId, SessionType.GROUP.getCode(), groupId),
                    plan
            );
        }
        evictAfterCommit(plan);
    }

    @Override
    @Transactional
    /**
     * 批量写会话配置时先确保记录存在，再按请求内容做局部字段更新。
     * 这里只维护用户会话元数据，不负责推进 seq 同步位点。
     */
    public void setConversations(List<String> userIds, SetConversationRequest request) {
        if (userIds == null || userIds.isEmpty() || request == null || isBlank(request.getConversationId())) {
            return;
        }
        Map<String, Object> fields = buildUpdateFields(request);
        ConversationCacheEvictPlan plan = new ConversationCacheEvictPlan();
        for (String userId : userIds) {
            UserConversation state = buildExplicitState(
                    userId,
                    request.getConversationId(),
                    request.getConversationType(),
                    request.getTargetId()
            );
            if (request.getRecvMsgOpt() != null) {
                state.setReceiveOpt(request.getRecvMsgOpt());
            }
            if (request.getPinned() != null) {
                state.setPinned(request.getPinned());
            }
            if (request.getAttachedInfo() != null) {
                state.setAttachedInfo(request.getAttachedInfo());
            }
            stateRepository.createIfAbsent(state);
            if (!fields.isEmpty()) {
                stateRepository.updateFields(userId, request.getConversationId(), fields);
            }
            plan.addDetail(userId, request.getConversationId());
            plan.addConversationIdsUser(userId);
            plan.addConversationIdsHashUser(userId);
            if (request.getPinned() != null) {
                plan.addPinnedUser(userId);
            }
            if (request.getRecvMsgOpt() != null) {
                plan.addNotNotifyUser(userId);
                plan.addNotReceiveConversation(request.getConversationId());
            }
        }
        evictAfterCommit(plan);
    }

    /**
     * 初始化单个用户在该会话下的基础状态，并登记相关缓存失效计划。
     * 这里不写用户 seq 位点，避免在建会话时伪造 0L 的 maxSeq。
     */
    private void createParticipantConversation(UserConversation conversation, ConversationCacheEvictPlan plan) {
        stateRepository.createIfAbsent(conversation);
        plan.addDetail(conversation.getOwnerUserId(), conversation.getConversationId());
        plan.addConversationIdsUser(conversation.getOwnerUserId());
        plan.addConversationIdsHashUser(conversation.getOwnerUserId());
        plan.addNotReceiveConversation(conversation.getConversationId());
    }

    /**
     * 构造写库所需的最小会话对象。
     */
    private UserConversation buildExplicitState(String ownerUserId, String conversationId, int conversationType, String targetId) {
        UserConversation state = new UserConversation();
        state.setOwnerUserId(ownerUserId);
        state.setConversationId(conversationId);
        state.setConversationType(conversationType);
        state.setTargetId(targetId);
        return state;
    }

    /**
     * 只收集本次请求真正需要更新的字段，避免覆盖未传字段。
     */
    private Map<String, Object> buildUpdateFields(SetConversationRequest request) {
        Map<String, Object> fields = new HashMap<>();
        if (request.getRecvMsgOpt() != null) {
            fields.put("receiveOpt", request.getRecvMsgOpt());
        }
        if (request.getPinned() != null) {
            fields.put("pinned", request.getPinned());
        }
        if (request.getAttachedInfo() != null) {
            fields.put("attachedInfo", request.getAttachedInfo());
        }
        return fields;
    }

    /**
     * 若当前存在事务，则在事务提交后统一删缓存；否则立即失效。
     */
    private void evictAfterCommit(ConversationCacheEvictPlan plan) {
        if (plan.isEmpty()) {
            return;
        }
        Runnable task = () -> {
            removeKeys(conversationDetailCache, plan.detailKeys);
            removeKeys(conversationIdsCache, plan.conversationIdsUsers);
            removeKeys(conversationIdsHashCache, plan.conversationIdsHashUsers);
            removeKeys(pinnedConversationIdsCache, plan.pinnedUsers);
            removeKeys(notNotifyConversationIdsCache, plan.notNotifyUsers);
            removeKeys(conversationNotReceiveUserIdsCache, plan.notReceiveConversationIds);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /**
     * 批量移除指定缓存键。
     */
    private <T> void removeKeys(Cache<String, T> cache, Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        cache.removeAll(keys);
    }

    /**
     * 基于统一配置创建会话缓存实例，避免在字段注解中散落重复配置。
     */
    private <T> Cache<String, T> createCache(String name) {
        QuickConfig quickConfig = QuickConfig.newBuilder(name)
                .expire(Duration.ofSeconds(REMOTE_EXPIRE_SECONDS))
                .localExpire(Duration.ofSeconds(LOCAL_EXPIRE_SECONDS))
                .cacheType(CacheType.REMOTE)
                .localLimit(LOCAL_LIMIT)
                .build();
        return cacheManager.getOrCreateCache(quickConfig);
    }

    /**
     * 统一的 detail cache key 格式。
     */
    private String detailKey(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }

    /**
     * 去重并过滤空白会话 ID，避免批量缓存/批量查询出现脏键。
     */
    private List<String> dedupeConversationIds(List<String> conversationIds) {
        return conversationIds.stream()
                .filter(conversationId -> conversationId != null && !conversationId.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    /**
     * 统一空白字符串判断。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 返回防御性副本，避免调用方直接修改缓存中的对象实例。
     */
    private UserConversation copy(UserConversation state) {
        if (state == null) {
            return null;
        }
        UserConversation copy = new UserConversation();
        copy.setOwnerUserId(state.getOwnerUserId());
        copy.setConversationId(state.getConversationId());
        copy.setConversationType(state.getConversationType());
        copy.setTargetId(state.getTargetId());
        copy.setReceiveOpt(state.getReceiveOpt());
        copy.setUnreadCount(state.getUnreadCount());
        copy.setPinned(state.isPinned());
        copy.setAttachedInfo(state.getAttachedInfo());
        copy.setGroupAtType(state.getGroupAtType());
        copy.setAutoCleanup(state.isAutoCleanup());
        copy.setCleanupCycle(state.getCleanupCycle());
        copy.setLatestCleanupTime(state.getLatestCleanupTime());
        copy.setCreatedAt(state.getCreatedAt());
        copy.setUpdatedAt(state.getUpdatedAt());
        return copy;
    }

    /**
     * 一次写操作涉及的缓存失效集合。
     */
    private static final class ConversationCacheEvictPlan {
        private final Set<String> detailKeys = new LinkedHashSet<>();
        private final Set<String> conversationIdsUsers = new LinkedHashSet<>();
        private final Set<String> conversationIdsHashUsers = new LinkedHashSet<>();
        private final Set<String> pinnedUsers = new LinkedHashSet<>();
        private final Set<String> notNotifyUsers = new LinkedHashSet<>();
        private final Set<String> notReceiveConversationIds = new LinkedHashSet<>();

        /** 记录单条会话详情缓存键。 */
        void addDetail(String ownerUserId, String conversationId) {
            detailKeys.add(ownerUserId + ":" + conversationId);
        }

        /** 记录需要失效会话 ID 列表缓存的用户。 */
        void addConversationIdsUser(String userId) {
            conversationIdsUsers.add(userId);
        }

        /** 记录需要失效会话 ID hash 缓存的用户。 */
        void addConversationIdsHashUser(String userId) {
            conversationIdsHashUsers.add(userId);
        }

        /** 记录需要失效置顶列表缓存的用户。 */
        void addPinnedUser(String userId) {
            pinnedUsers.add(userId);
        }

        /** 记录需要失效免提醒列表缓存的用户。 */
        void addNotNotifyUser(String userId) {
            notNotifyUsers.add(userId);
        }

        /** 记录需要失效不接收用户列表缓存的会话。 */
        void addNotReceiveConversation(String conversationId) {
            notReceiveConversationIds.add(conversationId);
        }

        /** 判断本次写操作是否真的涉及缓存失效。 */
        boolean isEmpty() {
            return detailKeys.isEmpty()
                    && conversationIdsUsers.isEmpty()
                    && conversationIdsHashUsers.isEmpty()
                    && pinnedUsers.isEmpty()
                    && notNotifyUsers.isEmpty()
                    && notReceiveConversationIds.isEmpty();
        }
    }
}
