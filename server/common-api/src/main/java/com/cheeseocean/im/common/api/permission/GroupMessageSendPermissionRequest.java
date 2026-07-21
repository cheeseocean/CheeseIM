package com.cheeseocean.im.common.api.permission;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 群消息发送权限批量请求。
 *
 * <p>postbox 通常只传一个 sender；postmaster 按同群消费批次合并 sender，
 * 避免每条消息单独发起 Dubbo 查询。</p>
 */
@Data
public class GroupMessageSendPermissionRequest implements Serializable {

    private String groupId;
    private List<String> senderIds;
}
