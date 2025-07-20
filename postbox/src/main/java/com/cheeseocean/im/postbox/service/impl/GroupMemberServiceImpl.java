package com.cheeseocean.im.postbox.service.impl;

import com.cheeseocean.im.postbox.service.GroupMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 群组成员服务实现
 * 基于Redis缓存实现群组成员管理
 * 
 * @author CheeseIM
 */
@Service
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupMemberServiceImpl.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
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
    private static final long CACHE_EXPIRE_SECONDS = 3600; // 1小时
    
    @Override
    public List<String> getGroupMembers(String groupID) {
        try {
            if (groupID == null || groupID.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            String key = GROUP_MEMBERS_KEY_PREFIX + groupID;
            
            // 先从缓存获取
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members != null && !members.isEmpty()) {
                List<String> memberList = new ArrayList<>();
                for (Object member : members) {
                    if (member instanceof String) {
                        memberList.add((String) member);
                    }
                }
                
                // 刷新缓存过期时间
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                
                logger.debug("从缓存获取群组成员: groupID={}, memberCount={}", groupID, memberList.size());
                return memberList;
            }
            
            // 缓存未命中，从数据库加载（这里模拟数据）
            List<String> members_from_db = loadGroupMembersFromDatabase(groupID);
            
            // 更新缓存
            if (!members_from_db.isEmpty()) {
                String[] memberArray = members_from_db.toArray(new String[0]);
                redisTemplate.opsForSet().add(key, (Object[]) memberArray);
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            
            logger.debug("从数据库加载群组成员: groupID={}, memberCount={}", groupID, members_from_db.size());
            return members_from_db;
            
        } catch (Exception e) {
            logger.error("获取群组成员失败: groupID={}", groupID, e);
            return new ArrayList<>();
        }
    }
    
    @Override
    public boolean isGroupMember(String groupID, String userID) {
        try {
            if (groupID == null || groupID.trim().isEmpty() || 
                userID == null || userID.trim().isEmpty()) {
                return false;
            }
            
            String key = GROUP_MEMBERS_KEY_PREFIX + groupID;
            Boolean isMember = redisTemplate.opsForSet().isMember(key, userID);
            
            if (isMember != null && isMember) {
                // 刷新缓存过期时间
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                return true;
            }
            
            // 如果缓存中没有，尝试从数据库加载
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
            
            String key = GROUP_MEMBERS_KEY_PREFIX + groupID;
            Long count = redisTemplate.opsForSet().size(key);
            
            if (count != null && count > 0) {
                // 刷新缓存过期时间
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                return count.intValue();
            }
            
            // 缓存未命中，从数据库加载
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
            
            // 先从缓存获取
            Set<Object> groups = redisTemplate.opsForSet().members(key);
            if (groups != null && !groups.isEmpty()) {
                List<String> groupList = new ArrayList<>();
                for (Object group : groups) {
                    if (group instanceof String) {
                        groupList.add((String) group);
                    }
                }
                
                // 刷新缓存过期时间
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                
                logger.debug("从缓存获取用户群组: userID={}, groupCount={}", userID, groupList.size());
                return groupList;
            }
            
            // 缓存未命中，从数据库加载（这里模拟数据）
            List<String> groups_from_db = loadUserGroupsFromDatabase(userID);
            
            // 更新缓存
            if (!groups_from_db.isEmpty()) {
                String[] groupArray = groups_from_db.toArray(new String[0]);
                redisTemplate.opsForSet().add(key, (Object[]) groupArray);
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            
            logger.debug("从数据库加载用户群组: userID={}, groupCount={}", userID, groups_from_db.size());
            return groups_from_db;
            
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
            
            // 从缓存获取群组信息
            Map<Object, Object> groupInfoMap = redisTemplate.opsForHash().entries(key);
            if (groupInfoMap != null && !groupInfoMap.isEmpty()) {
                GroupInfo groupInfo = mapToGroupInfo(groupInfoMap);
                
                // 刷新缓存过期时间
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                
                logger.debug("从缓存获取群组信息: groupID={}", groupID);
                return groupInfo;
            }
            
            // 缓存未命中，从数据库加载（这里模拟数据）
            GroupInfo groupInfo = loadGroupInfoFromDatabase(groupID);
            
            // 更新缓存
            if (groupInfo != null) {
                Map<String, Object> infoMap = groupInfoToMap(groupInfo);
                redisTemplate.opsForHash().putAll(key, infoMap);
                redisTemplate.expire(key, CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
            
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
    
    /**
     * Map转GroupInfo
     */
    private GroupInfo mapToGroupInfo(Map<Object, Object> map) {
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setGroupID((String) map.get("groupID"));
        groupInfo.setGroupName((String) map.get("groupName"));
        groupInfo.setGroupType((String) map.get("groupType"));
        groupInfo.setOwnerUserID((String) map.get("ownerUserID"));
        
        Object memberCount = map.get("memberCount");
        if (memberCount instanceof Integer) {
            groupInfo.setMemberCount((Integer) memberCount);
        } else if (memberCount instanceof String) {
            try {
                groupInfo.setMemberCount(Integer.parseInt((String) memberCount));
            } catch (NumberFormatException e) {
                logger.warn("无效的成员数量格式: {}", memberCount);
            }
        }
        
        Object createTime = map.get("createTime");
        if (createTime instanceof Long) {
            groupInfo.setCreateTime((Long) createTime);
        } else if (createTime instanceof String) {
            try {
                groupInfo.setCreateTime(Long.parseLong((String) createTime));
            } catch (NumberFormatException e) {
                logger.warn("无效的创建时间格式: {}", createTime);
            }
        }
        
        groupInfo.setStatus((String) map.get("status"));
        return groupInfo;
    }
    
    /**
     * GroupInfo转Map
     */
    private Map<String, Object> groupInfoToMap(GroupInfo groupInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("groupID", groupInfo.getGroupID());
        map.put("groupName", groupInfo.getGroupName());
        map.put("groupType", groupInfo.getGroupType());
        map.put("ownerUserID", groupInfo.getOwnerUserID());
        map.put("memberCount", groupInfo.getMemberCount());
        map.put("createTime", groupInfo.getCreateTime());
        map.put("status", groupInfo.getStatus());
        return map;
    }
}
