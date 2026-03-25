package com.cheeseocean.im.postman.provider;

import com.cheeseocean.im.common.core.enums.PlatformType;
import com.cheeseocean.im.postman.entity.PushMessage;

import java.util.List;
import java.util.Map;

/**
 * 推送提供商服务接口
 * 定义推送提供商的通用接口，支持多种推送服务
 *
 * @author CheeseIM
 */
public interface PushProvider {

    /**
     * 发送推送消息
     *
     * @param pushMessage 推送消息
     * @return 推送结果
     */
    PushResult sendPush(PushMessage pushMessage);

    /**
     * 获取推送提供商名称
     *
     * @return 提供商名称
     */
    String getProviderName();

    /**
     * 获取支持的平台列表
     *
     * @return 支持的平台列表
     */
    List<PlatformType> getSupportedPlatforms();

    /**
     * 检查是否支持指定平台
     *
     * @param platformType 平台类型
     * @return 是否支持
     */
    boolean supportsPlatform(PlatformType platformType);

    /**
     * 检查推送服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取推送提供商配置信息
     *
     * @return 配置信息
     */
    ProviderConfig getConfig();

    /**
     * 推送结果类
     */
    class PushResult {
        private boolean success;
        private String  errorMessage;
        private String  messageID;
        private Long    responseTime;
        private String  platformCode;

        public PushResult() {
        }

        public PushResult(boolean success, String errorMessage, String platformCode) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.platformCode = platformCode;
        }

        public static PushResult success(String platformCode) {
            return new PushResult(true, null, platformCode);
        }

        public static PushResult success(String messageID, String platformCode) {
            PushResult result = new PushResult(true, null, platformCode);
            result.setMessageID(messageID);
            return result;
        }

        public static PushResult failure(String errorMessage, String platformCode) {
            return new PushResult(false, errorMessage, platformCode);
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

        public String getMessageID() {
            return messageID;
        }

        public void setMessageID(String messageID) {
            this.messageID = messageID;
        }

        public Long getResponseTime() {
            return responseTime;
        }

        public void setResponseTime(Long responseTime) {
            this.responseTime = responseTime;
        }

        public String getPlatformCode() {
            return platformCode;
        }

        public void setPlatformCode(String platformCode) {
            this.platformCode = platformCode;
        }

        @Override
        public String toString() {
            return "PushResult{" +
                    "success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", messageID='" + messageID + '\'' +
                    ", responseTime=" + responseTime +
                    ", platformCode='" + platformCode + '\'' +
                    '}';
        }
    }

    /**
     * 推送提供商配置类
     */
    class ProviderConfig {
        private String              providerName;
        private boolean             enabled;
        private List<PlatformType>  supportedPlatforms;
        private Map<String, Object> properties;
        private long                lastUpdateTime;

        public ProviderConfig() {
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public ProviderConfig(String providerName, boolean enabled) {
            this();
            this.providerName = providerName;
            this.enabled = enabled;
        }

        // Getter and Setter
        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<PlatformType> getSupportedPlatforms() {
            return supportedPlatforms;
        }

        public void setSupportedPlatforms(List<PlatformType> supportedPlatforms) {
            this.supportedPlatforms = supportedPlatforms;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(long lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }

        @Override
        public String toString() {
            return "ProviderConfig{" +
                    "providerName='" + providerName + '\'' +
                    ", enabled=" + enabled +
                    ", supportedPlatforms=" + supportedPlatforms +
                    ", properties=" + properties +
                    ", lastUpdateTime=" + lastUpdateTime +
                    '}';
        }
    }
}
