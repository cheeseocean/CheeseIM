package com.cheeseocean.im.postman.entity;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 离线推送结果类
 * 
 * @author xxxcrel
 */
public class OfflinePushResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 推送是否成功
     */
    private boolean success;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 推送成功的用户列表
     */
    private List<String> successUsers;
    
    /**
     * 推送失败的用户列表
     */
    private List<String> failedUsers;
    
    /**
     * 用户错误信息映射
     */
    private Map<String, String> userErrors;
    
    /**
     * 推送提供商结果映射
     */
    private Map<String, String> providerResults;
    
    /**
     * 总响应时间（毫秒）
     */
    private Long totalResponseTime;
    
    /**
     * 推送类型：SUCCESS, FAILURE, PARTIAL
     */
    private PushResultType resultType;
    
    public enum PushResultType {
        SUCCESS,    // 全部成功
        FAILURE,    // 全部失败
        PARTIAL     // 部分成功
    }
    
    public OfflinePushResult() {
    }
    
    public OfflinePushResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.resultType = success ? PushResultType.SUCCESS : PushResultType.FAILURE;
    }
    
    /**
     * 创建成功结果
     */
    public static OfflinePushResult success(List<String> successUsers) {
        OfflinePushResult result = new OfflinePushResult();
        result.success = true;
        result.successUsers = successUsers;
        result.resultType = PushResultType.SUCCESS;
        return result;
    }
    
    /**
     * 创建失败结果
     */
    public static OfflinePushResult failure(String errorMessage) {
        OfflinePushResult result = new OfflinePushResult();
        result.success = false;
        result.errorMessage = errorMessage;
        result.resultType = PushResultType.FAILURE;
        return result;
    }
    
    /**
     * 创建部分成功结果
     */
    public static OfflinePushResult partial(List<String> successUsers, List<String> failedUsers, Map<String, String> userErrors) {
        OfflinePushResult result = new OfflinePushResult();
        result.success = successUsers != null && !successUsers.isEmpty();
        result.successUsers = successUsers;
        result.failedUsers = failedUsers;
        result.userErrors = userErrors;
        result.resultType = PushResultType.PARTIAL;
        return result;
    }
    
    // Getter and Setter methods
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public List<String> getSuccessUsers() {
        return successUsers;
    }
    
    public void setSuccessUsers(List<String> successUsers) {
        this.successUsers = successUsers;
    }
    
    public List<String> getFailedUsers() {
        return failedUsers;
    }
    
    public void setFailedUsers(List<String> failedUsers) {
        this.failedUsers = failedUsers;
    }
    
    public Map<String, String> getUserErrors() {
        return userErrors;
    }
    
    public void setUserErrors(Map<String, String> userErrors) {
        this.userErrors = userErrors;
    }
    
    public Map<String, String> getProviderResults() {
        return providerResults;
    }
    
    public void setProviderResults(Map<String, String> providerResults) {
        this.providerResults = providerResults;
    }
    
    public Long getTotalResponseTime() {
        return totalResponseTime;
    }
    
    public void setTotalResponseTime(Long totalResponseTime) {
        this.totalResponseTime = totalResponseTime;
    }
    
    public PushResultType getResultType() {
        return resultType;
    }
    
    public void setResultType(PushResultType resultType) {
        this.resultType = resultType;
    }
    
    @Override
    public String toString() {
        return "OfflinePushResult{" +
                "success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", successUsers=" + (successUsers != null ? successUsers.size() : 0) +
                ", failedUsers=" + (failedUsers != null ? failedUsers.size() : 0) +
                ", resultType=" + resultType +
                ", totalResponseTime=" + totalResponseTime +
                '}';
    }
}
