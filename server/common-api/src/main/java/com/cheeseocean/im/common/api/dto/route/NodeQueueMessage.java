package com.cheeseocean.im.common.api.dto.route;

import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;

import java.io.Serializable;

/**
 * postoffice 节点队列通用 envelope。
 *
 * <p>payload 保持 JSON 字符串，以适配 Redis LIST 的字符串存储模型；消费者必须先按
 * {@link #type} 识别消息类型，再反序列化为对应的业务 payload，禁止消费裸 payload。
 */
public class NodeQueueMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型码，见 {@link NodeQueueMessageType#getCode()}。
     */
    private Integer type;

    /**
     * 具体业务 payload 的 JSON 字符串。
     */
    private String payload;

    public static NodeQueueMessage of(NodeQueueMessageType type, String payload) {
        NodeQueueMessage message = new NodeQueueMessage();
        message.setType(type == null ? null : type.getCode());
        message.setPayload(payload);
        return message;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
