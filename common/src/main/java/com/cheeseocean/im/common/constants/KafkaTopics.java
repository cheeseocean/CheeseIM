package com.cheeseocean.im.common.constants;

/**
 * Kafka Topic 常量
 * 
 * @author CheeseIM
 */
public class KafkaTopics {
    
    /**
     * 消息发送到Redis的Topic (对应open-im-server的toRedisTopic)
     */
    public static final String TO_REDIS_TOPIC = "cheese_im_to_redis";
    
    /**
     * 消息推送的Topic (对应open-im-server的toPushTopic)
     */
    public static final String TO_PUSH_TOPIC = "cheese_im_to_push";
    
    /**
     * 消息存储到MongoDB的Topic (对应open-im-server的toMongoTopic)
     */
    public static final String TO_MONGO_TOPIC = "cheese_im_to_mongo";
    
    /**
     * 消息状态更新Topic
     */
    public static final String MSG_STATUS_UPDATE_TOPIC = "cheese_im_msg_status_update";
    
    /**
     * 用户在线状态Topic
     */
    public static final String USER_ONLINE_STATUS_TOPIC = "cheese_im_user_online_status";
    
    private KafkaTopics() {
        // 私有构造函数，防止实例化
    }
}
