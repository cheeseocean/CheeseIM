package com.cheeseocean.im.common.core.constants;

public final class TopicNames {

    public static final String INGRESS               = "ingress";
    public static final String HISTORY               = "history";
    public static final String DELIVERY              = "delivery";
    public static final String DELIVERY_OUTCOME      = "delivery-outcome";
    public static final String GROUP_FANOUT          = "group-fanout";
    public static final String OFFLINE_PUSH          = "offlinepush";

    private static final java.util.Set<String> MANAGED_TOPICS = java.util.Set.of(
            INGRESS,
            HISTORY,
            DELIVERY,
            DELIVERY_OUTCOME,
            GROUP_FANOUT,
            OFFLINE_PUSH);

    public static boolean isManagedTopic(String topic) {
        return topic != null && MANAGED_TOPICS.contains(topic);
    }

    public static java.util.Set<String> managedTopics() {
        return MANAGED_TOPICS;
    }

    private TopicNames() {
    }
}
