package com.cheeseocean.im.common.constants;

/**
 * Canonical Kafka topic names for the rebuilt IM message flow.
 * Keep compatibility aliases during the migration, but new code should use the short canonical names.
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

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_INGRESS_TOPIC = INGRESS;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_HISTORY_TOPIC = HISTORY;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_DELIVERY_TOPIC = DELIVERY;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_GROUP_FANOUT_TOPIC = GROUP_FANOUT;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_RECEIPT_TOPIC = RECEIPT;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String OFFLINE_PUSH_TOPIC = OFFLINE_PUSH;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_RETRY_TOPIC = RETRY;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String MESSAGE_DLQ_TOPIC = DLQ;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String PERSISTENT_TOPIC = HISTORY;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String DELIVERY_COMPENSATION_TOPIC = RETRY;

    @Deprecated(since = "2026-03-21", forRemoval = false)
    public static final String DELIVERY_DEAD_LETTER_TOPIC = DLQ;

    private KafkaTopics() {
    }
}
