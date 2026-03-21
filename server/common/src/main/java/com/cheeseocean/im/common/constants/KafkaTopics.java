package com.cheeseocean.im.common.constants;

/**
 * Canonical Kafka topic names for the rebuilt IM message flow.
 */
public final class KafkaTopics {

    private static final String MESSAGE_PREFIX = "im.message.";

    public static final String INGRESS = MESSAGE_PREFIX + "ingress";
    public static final String HISTORY = MESSAGE_PREFIX + "history";
    public static final String DELIVERY = MESSAGE_PREFIX + "delivery";
    public static final String GROUP_FANOUT = MESSAGE_PREFIX + "group_fanout";
    public static final String RECEIPT = MESSAGE_PREFIX + "receipt";
    public static final String OFFLINE_PUSH = MESSAGE_PREFIX + "offline_push";
    public static final String RETRY = MESSAGE_PREFIX + "retry";
    public static final String DLQ = MESSAGE_PREFIX + "dlq";

    private KafkaTopics() {
    }
}
