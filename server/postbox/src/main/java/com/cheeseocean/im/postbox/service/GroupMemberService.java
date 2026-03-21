package com.cheeseocean.im.postbox.service;

import java.util.List;

/**
 * Resolves member IDs for group fanout planning.
 */
public interface GroupMemberService {

    /**
     * Resolves conversation-scoped members for async group ingress.
     */
    List<String> queryConversationMembers(String conversationId);
    
    /**
     * 获取群组成员列表
     * 
     * @param groupID 群组ID
     * @return 成员用户ID列表
     */
    List<String> getGroupMembers(String groupID);
    
    /**
     * 检查用户是否为群组成员
     * 
     * @param groupID 群组ID
     * @param userID 用户ID
     * @return 是否为群组成员
     */
    boolean isGroupMember(String groupID, String userID);
    
    /**
     * 获取群组成员数量
     * 
     * @param groupID 群组ID
     * @return 成员数量
     */
    int getGroupMemberCount(String groupID);
    
    /**
     * 获取用户加入的群组列表
     * 
     * @param userID 用户ID
     * @return 群组ID列表
     */
    List<String> getUserGroups(String userID);
    
    /**
     * 批量获取群组成员列表
     * 
     * @param groupIDs 群组ID列表
     * @return 群组ID -> 成员列表的映射
     */
    java.util.Map<String, List<String>> batchGetGroupMembers(List<String> groupIDs);
    
    /**
     * 获取群组信息
     * 
     * @param groupID 群组ID
     * @return 群组信息
     */
    GroupInfo getGroupInfo(String groupID);
    
    /**
     * 群组信息类
     */
    class GroupInfo {
        private String groupID;
        private String groupName;
        private String groupType;
        private String ownerUserID;
        private Integer memberCount;
        private Long createTime;
        private String status;
        
        public GroupInfo() {}
        
        public GroupInfo(String groupID, String groupName) {
            this.groupID = groupID;
            this.groupName = groupName;
            this.createTime = System.currentTimeMillis();
            this.status = "active";
        }
        
        // Getter and Setter
        public String getGroupID() {
            return groupID;
        }
        
        public void setGroupID(String groupID) {
            this.groupID = groupID;
        }
        
        public String getGroupName() {
            return groupName;
        }
        
        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }
        
        public String getGroupType() {
            return groupType;
        }
        
        public void setGroupType(String groupType) {
            this.groupType = groupType;
        }
        
        public String getOwnerUserID() {
            return ownerUserID;
        }
        
        public void setOwnerUserID(String ownerUserID) {
            this.ownerUserID = ownerUserID;
        }
        
        public Integer getMemberCount() {
            return memberCount;
        }
        
        public void setMemberCount(Integer memberCount) {
            this.memberCount = memberCount;
        }
        
        public Long getCreateTime() {
            return createTime;
        }
        
        public void setCreateTime(Long createTime) {
            this.createTime = createTime;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        @Override
        public String toString() {
            return "GroupInfo{" +
                    "groupID='" + groupID + '\'' +
                    ", groupName='" + groupName + '\'' +
                    ", groupType='" + groupType + '\'' +
                    ", ownerUserID='" + ownerUserID + '\'' +
                    ", memberCount=" + memberCount +
                    ", createTime=" + createTime +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}
