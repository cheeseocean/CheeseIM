package com.cheeseocean.im.common.core.store.sequence.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 会话 seq 分配配置。
 *
 * <p>这组配置专门服务于消息 seq 分配，不复用通用 ID 生成器配置。
 */
@ConfigurationProperties(prefix = "cheeseim.conversation-seq")
public class ConversationSeqAllocatorProperties {

    private ConversationSeqDeploymentMode deploymentMode = ConversationSeqDeploymentMode.STANDALONE;
    private int singleReserveSize = 50;
    private int groupReserveSize = 100;
    private int maxRetries = 10;
    private long lockTtlSeconds = 3L;
    private long dataTtlSeconds = 31_536_000L;

    public ConversationSeqDeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(ConversationSeqDeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    public int getSingleReserveSize() {
        return singleReserveSize;
    }

    public void setSingleReserveSize(int singleReserveSize) {
        this.singleReserveSize = singleReserveSize;
    }

    public int getGroupReserveSize() {
        return groupReserveSize;
    }

    public void setGroupReserveSize(int groupReserveSize) {
        this.groupReserveSize = groupReserveSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    public void setLockTtlSeconds(long lockTtlSeconds) {
        this.lockTtlSeconds = lockTtlSeconds;
    }

    public long getDataTtlSeconds() {
        return dataTtlSeconds;
    }

    public void setDataTtlSeconds(long dataTtlSeconds) {
        this.dataTtlSeconds = dataTtlSeconds;
    }
}
