package com.cheeseocean.im.common.constants;

/**
 * Kafka Topic 常量
 * 
 * @author CheeseIM
 */
public class KafkaTopics {
    
    /**
     * 消息发送到Kafka的Topic
     */
    public static final String MSG_TOPIC = "cheese_im_msg";
    
    /**
     * 消息推送的Topic
     */
    public static final String PUSH_TOPIC = "cheese_im_push";

    /**
     * 离线推送Topic
     */
    public static final String OFFLINE_PUSH_TOPIC = "cheese_im_offline_push";

    /**
     * 持久化消息的Topic
     */
    public static final String PERSISTENT_TOPIC = "cheese_im_persistent";
    
    /**
     * 消息状态更新Topic
     */
    public static final String MSG_STATUS_UPDATE_TOPIC = "cheese_im_msg_status_update";

    /**
     * 用户在线状态Topic
     */
    public static final String USER_ONLINE_STATUS_TOPIC = "cheese_im_user_online_status";

    /**
     * 投递补偿Topic
     */
    public static final String DELIVERY_COMPENSATION_TOPIC = "cheese_im_delivery_compensation";

    /**
     * 投递死信Topic
     */
    public static final String DELIVERY_DEAD_LETTER_TOPIC = "cheese_im_delivery_dead_letter";

    public static final String MESSAGE_INGRESS_TOPIC = "im.message.ingress";
    public static final String MESSAGE_HISTORY_TOPIC = "im.message.history";
    public static final String MESSAGE_DELIVERY_TOPIC = "im.message.delivery";
    public static final String MESSAGE_GROUP_FANOUT_TOPIC = "im.message.group_fanout";
    public static final String MESSAGE_RECEIPT_TOPIC = "im.message.receipt";
    public static final String MESSAGE_RETRY_TOPIC = "im.message.retry";
    public static final String MESSAGE_DLQ_TOPIC = "im.message.dlq";

    private KafkaTopics() {
        // 私有构造函数，防止实例化
    }
}
