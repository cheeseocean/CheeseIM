package com.cheeseocean.im.common.api.dto.dispatch;

import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import lombok.Data;

import java.io.Serializable;

/**
 * 指定用户的在线控制通知投递请求。
 *
 * <p>控制通知不进入消息历史和离线推送链路；{@code deliveryId} 由调用方稳定生成，
 * 供网关按连接去重。
 */
@Data
public class ControlNotificationReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private ServerEnvelope envelope;
    private String deliveryId;
}
