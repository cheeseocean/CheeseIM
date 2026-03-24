package com.cheeseocean.im.postbox.service.impl;

import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.postbox.service.GroupMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 群组成员服务实现
 * 基于Redis缓存实现群组成员管理
 * 
 * @author CheeseIM
 */
@Service
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupMemberServiceImpl.class);
    
    private final MultiLevelCacheService cacheService;
    
    /**
     * 群组成员缓存Key前缀
     */
    private static final String GROUP_MEMBERS_KEY_PREFIX = "cheese_im:group:members:";
    
    /**
     * 用户群组缓存Key前缀
     */
    private static final String USER_GROUPS_KEY_PREFIX = "cheese_im:user:groups:";
    
    /**
     * 群组信息缓存Key前缀
     */
    private static final String GROUP_INFO_KEY_PREFIX = "cheese_im:group:info:";
    
    /**
     * 缓存过期时间（秒）
     */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    public GroupMemberServiceImpl(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public List<String> queryConversationMembers(String conversationId) {
        return getGroupMembers(normalizeConversationId(conversationId));
    }
    
    @Override
    public List<String> getGroupMembers(String groupID) {
        try {
            if (groupID == null || groupID.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            String key = GROUP_MEMBERS_KEY_PREFIX + groupID;
            List<String> members_from_db = cacheService.getOrLoad(key, List.class, CACHE_TTL, () -> new ArrayList<>(loadGroupMembersFromDatabase(groupID)));
            logger.debug("从数据库加载群组成员: groupID={}, memberCount={}", groupID, members_from_db.size());
            return castStringList(members_from_db);
            
        } catch (Exception e) {
            logger.error("获取群组成员失败: groupID={}", groupID, e);
            throw new IllegalStateException("Failed to load group members for " + groupID, e);
        }
    }
    
    @Override
    public boolean isGroupMember(String groupID, String userID) {
        try {
            if (groupID == null || groupID.trim().isEmpty() || 
                userID == null || userID.trim().isEmpty()) {
                return false;
            }
            
            List<String> members = getGroupMembers(groupID);
            return members.contains(userID);
            
        } catch (Exception e) {
            logger.error("检查群组成员失败: groupID={}, userID={}", groupID, userID, e);
            return false;
        }
    }
    
    @Override
    public int getGroupMemberCount(String groupID) {
        try {
            if (groupID == null || groupID.trim().isEmpty()) {
                return 0;
            }
            
            List<String> members = getGroupMembers(groupID);
            return members.size();
            
        } catch (Exception e) {
            logger.error("获取群组成员数量失败: groupID={}", groupID, e);
            return 0;
        }
    }
    
    @Override
    public List<String> getUserGroups(String userID) {
        try {
            if (userID == null || userID.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            String key = USER_GROUPS_KEY_PREFIX + userID;
            List<String> groups_from_db = cacheService.getOrLoad(key, List.class, CACHE_TTL, () -> new ArrayList<>(loadUserGroupsFromDatabase(userID)));
            logger.debug("从数据库加载用户群组: userID={}, groupCount={}", userID, groups_from_db.size());
            return castStringList(groups_from_db);
            
        } catch (Exception e) {
            logger.error("获取用户群组失败: userID={}", userID, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public Map<String, List<String>> batchGetGroupMembers(List<String> groupIDs) {
        Map<String, List<String>> result = new HashMap<>();
        
        if (groupIDs == null || groupIDs.isEmpty()) {
            return result;
        }
        
        for (String groupID : groupIDs) {
            List<String> members = getGroupMembers(groupID);
            result.put(groupID, members);
        }
        
        return result;
    }
    
    @Override
    public GroupInfo getGroupInfo(String groupID) {
        try {
            if (groupID == null || groupID.trim().isEmpty()) {
                return null;
            }
            
            String key = GROUP_INFO_KEY_PREFIX + groupID;
            GroupInfo groupInfo = cacheService.getOrLoad(key, GroupInfo.class, CACHE_TTL, () -> loadGroupInfoFromDatabase(groupID));
            logger.debug("从数据库加载群组信息: groupID={}", groupID);
            return groupInfo;
            
        } catch (Exception e) {
            logger.error("获取群组信息失败: groupID={}", groupID, e);
            return null;
        }
    }
    
    /**
     * 从数据库加载群组成员（模拟实现）
     */
    private List<String> loadGroupMembersFromDatabase(String groupID) {
        // 这里应该调用数据库或其他服务获取群组成员
        // 为了演示，返回模拟数据
        List<String> members = new ArrayList<>();
        
        // 模拟一些测试数据
        if ("group001".equals(groupID)) {
            members.add("user001");
            members.add("user002");
            members.add("user003");
        } else if ("group002".equals(groupID)) {
            members.add("user001");
            members.add("user004");
            members.add("user005");
        }
        
        return members;
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        if (conversationId.startsWith("group:")) {
            return conversationId.substring("group:".length());
        }
        return conversationId;
    }
    
    /**
     * 从数据库加载用户群组（模拟实现）
     */
    private List<String> loadUserGroupsFromDatabase(String userID) {
        // 这里应该调用数据库或其他服务获取用户群组
        // 为了演示，返回模拟数据
        List<String> groups = new ArrayList<>();
        
        // 模拟一些测试数据
        if ("user001".equals(userID)) {
            groups.add("group001");
            groups.add("group002");
        } else if ("user002".equals(userID)) {
            groups.add("group001");
        }
        
        return groups;
    }
    
    /**
     * 从数据库加载群组信息（模拟实现）
     */
    private GroupInfo loadGroupInfoFromDatabase(String groupID) {
        // 这里应该调用数据库或其他服务获取群组信息
        // 为了演示，返回模拟数据
        if ("group001".equals(groupID)) {
            GroupInfo groupInfo = new GroupInfo(groupID, "测试群组1");
            groupInfo.setGroupType("normal");
            groupInfo.setOwnerUserID("user001");
            groupInfo.setMemberCount(3);
            return groupInfo;
        } else if ("group002".equals(groupID)) {
            GroupInfo groupInfo = new GroupInfo(groupID, "测试群组2");
            groupInfo.setGroupType("normal");
            groupInfo.setOwnerUserID("user001");
            groupInfo.setMemberCount(3);
            return groupInfo;
        }
        
        return null;
    }
    
    private List<String> castStringList(List<?> raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        for (Object item : raw) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }
}
