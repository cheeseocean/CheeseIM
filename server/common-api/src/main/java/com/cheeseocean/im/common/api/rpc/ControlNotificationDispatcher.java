package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.dispatch.ControlNotificationReq;

/**
 * 控制通知的跨节点在线投递入口。
 *
 * <p>实现只向请求指定用户的在线连接投递，不触发离线推送。
 */
public interface ControlNotificationDispatcher {

    /**
     * @return 至少一个目标节点成功入队或本地网关成功受理时返回 {@code true}
     */
    boolean dispatch(ControlNotificationReq request);
}
