package com.cheeseocean.im.postoffice.api.param;

import java.io.Serializable;
import java.util.List;

/**
 * 批量获取用户在线状态响应
 *
 * @author CheeseIM
 */
public class UsersOnlineStatusResp implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户在线状态列表
     */
    private List<UserOnlineStatus> successList;

    private List<String> failedList;

    public UsersOnlineStatusResp() {
    }

    public UsersOnlineStatusResp(List<UserOnlineStatus> successList) {
        this.successList = successList;
    }

    public List<UserOnlineStatus> getSuccessList() {
        return successList;
    }

    public void setSuccessList(List<UserOnlineStatus> successList) {
        this.successList = successList;
    }

    public List<String> getFailedList() {
        return failedList;
    }

    public void setFailedList(List<String> failedList) {
        this.failedList = failedList;
    }

    /**
     * 用户在线状态信息
     */
    public static class UserOnlineStatus implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 用户ID
         */
        private String userID;

        /**
         * 在线状态 (online/offline)
         */
        private String status;

        /**
         * 在线平台详情列表
         */
        private List<PlatformStatus> platformIDs;

        public UserOnlineStatus() {
        }

        public UserOnlineStatus(String userID, String status, List<PlatformStatus> platformIDs) {
            this.userID = userID;
            this.status = status;
            this.platformIDs = platformIDs;
        }

        public String getUserID() {
            return userID;
        }

        public void setUserID(String userID) {
            this.userID = userID;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public List<PlatformStatus> getPlatformIDs() {
            return platformIDs;
        }

        public void setPlatformIDs(List<PlatformStatus> platformIDs) {
            this.platformIDs = platformIDs;
        }
    }

    /**
     * 平台在线状态
     */
    public static class PlatformStatus implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台ID
         */
        private Integer platformID;

        /**
         * token
         */
        private String token;

        /**
         * 连接ID列表
         */
        private String connID;

        public PlatformStatus() {
        }

        public PlatformStatus(Integer platformID, String token, String connID) {
            this.platformID = platformID;
            this.token = token;
            this.connID = connID;
        }

        public Integer getPlatformID() {
            return platformID;
        }

        public void setPlatformID(Integer platformID) {
            this.platformID = platformID;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getConnID() {
            return connID;
        }

        public void setConnID(String connID) {
            this.connID = connID;
        }
    }
}