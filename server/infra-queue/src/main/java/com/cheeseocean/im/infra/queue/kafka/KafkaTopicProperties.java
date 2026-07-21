package com.cheeseocean.im.infra.queue.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka 主链路 topic 与 DLT 的部署契约。 */
@Data
@ConfigurationProperties(prefix = "cheeseim.queue.kafka.topics")
public class KafkaTopicProperties {
    private boolean autoCreateEnabled;
    private int partitions = 12;
    private short replicationFactor = 3;
    private long retentionMs = 604_800_000L;
    private int minInSyncReplicas = 2;
}
