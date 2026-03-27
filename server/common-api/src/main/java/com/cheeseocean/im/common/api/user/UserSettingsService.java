package com.cheeseocean.im.common.api.user;

/**
 * 用户级设置的 Dubbo 服务接口。
 *
 * <p>取值使用 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt} 枚举。
 * MongoDB/Kafka/HTTP 传输使用整数 code；应用逻辑中用 {@code RecvMsgOpt.fromCode()} 转换比较。
 */
public interface UserSettingsService {

    /**
     * 返回用户的全局消息接收选项 code。
     * 未设置时返回 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt#RECEIVE RECEIVE}（0）。
     */
    int getGlobalRecvMsgOpt(String userId);

    /**
     * @param opt {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt} 的整数 code
     */
    void setGlobalRecvMsgOpt(String userId, int opt);
}
