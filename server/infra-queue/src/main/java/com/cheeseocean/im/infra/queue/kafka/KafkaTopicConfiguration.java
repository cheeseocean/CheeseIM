package com.cheeseocean.im.infra.queue.kafka;

import com.cheeseocean.im.common.core.constants.TopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;

import java.util.List;
import java.util.Map;

/**
 * Kafka 主 topic 和 DLT 契约。
 *
 * <p>应用自动创建是可选运维策略；cluster 模式无论是否创建都强制校验实际 topic，
 * 生产环境可由 Terraform/脚本预创建，不能把 DDL 权限强加给每个业务 Pod。</p>
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnProperty(prefix = "cheeseim.queue", name = "type", havingValue = "kafka")
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaTopicConfiguration {

    private static final List<String> TOPICS = List.of(
            TopicNames.INGRESS, TopicNames.HISTORY, TopicNames.DELIVERY,
            TopicNames.DELIVERY_OUTCOME, TopicNames.GROUP_FANOUT, TopicNames.OFFLINE_PUSH);

    @Bean
    @ConditionalOnProperty(prefix = "cheeseim.queue.kafka.topics", name = "auto-create-enabled", havingValue = "true")
    public KafkaAdmin.NewTopics cheeseImTopics(KafkaTopicProperties properties) {
        NewTopic[] topics = topicDeclarations(properties).toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }

    List<NewTopic> topicDeclarations(KafkaTopicProperties properties) {
        return TOPICS.stream()
                .flatMap(topic -> java.util.stream.Stream.of(topic, topic + ".DLT"))
                .map(topic -> topic(topic, properties))
                .toList();
    }

    @Bean
    public Admin cheeseImKafkaAdminClient(KafkaProperties kafkaProperties) {
        return Admin.create(kafkaProperties.buildAdminProperties(null));
    }

    @Bean
    public ApplicationRunner kafkaTopicContractValidator(KafkaTopicProperties properties,
                                                          Environment environment,
                                                          Admin admin) {
        return arguments -> {
            boolean cluster = "cluster".equalsIgnoreCase(environment.getProperty("cheeseim.runtime.mode"));
            long fanoutCompletedRetentionSeconds = environment.getProperty(
                    "cheeseim.delivery.group-fanout.completed-retention-seconds",
                    Long.class,
                    691_200L);
            if (properties.getPartitions() <= 0 || properties.getReplicationFactor() <= 0
                    || properties.getMinInSyncReplicas() <= 0
                    || properties.getMinInSyncReplicas() > properties.getReplicationFactor()) {
                throw new IllegalStateException("Kafka topic 分区、副本或 minISR 配置无效");
            }
            long minimumFanoutRetentionSeconds =
                    Math.floorDiv(properties.getRetentionMs(), 1_000L) + 1L;
            if (fanoutCompletedRetentionSeconds < minimumFanoutRetentionSeconds) {
                throw new IllegalStateException(
                        "群扩散完成状态保留时间必须大于 Kafka topic retention");
            }
            if (cluster) {
                validateActualTopics(admin, properties);
            }
        };
    }

    void validateActualTopics(Admin admin, KafkaTopicProperties properties) throws Exception {
        List<String> names = TOPICS.stream()
                .flatMap(topic -> java.util.stream.Stream.of(topic, topic + ".DLT"))
                .toList();
        Map<String, org.apache.kafka.clients.admin.TopicDescription> descriptions =
                admin.describeTopics(names).allTopicNames().get();
        for (String name : names) {
            var description = descriptions.get(name);
            if (description == null || description.partitions().size() != properties.getPartitions()
                    || description.partitions().stream().anyMatch(partition ->
                    partition.replicas().size() != properties.getReplicationFactor())) {
                throw new IllegalStateException("Kafka topic 分区或副本不符合契约: " + name);
            }
        }
        List<ConfigResource> resources = names.stream()
                .map(name -> new ConfigResource(ConfigResource.Type.TOPIC, name)).toList();
        Map<ConfigResource, Config> configs = admin.describeConfigs(resources).all().get();
        for (ConfigResource resource : resources) {
            Config config = configs.get(resource);
            String minIsr = configValue(config, TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG);
            String retention = configValue(config, TopicConfig.RETENTION_MS_CONFIG);
            if (!Integer.toString(properties.getMinInSyncReplicas()).equals(minIsr)
                    || !Long.toString(properties.getRetentionMs()).equals(retention)) {
                throw new IllegalStateException("Kafka topic minISR/retention 不符合契约: " + resource.name());
            }
        }
    }

    private String configValue(Config config, String key) {
        ConfigEntry entry = config == null ? null : config.get(key);
        return entry == null ? null : entry.value();
    }

    private NewTopic topic(String name, KafkaTopicProperties properties) {
        return TopicBuilder.name(name)
                .partitions(properties.getPartitions())
                .replicas(properties.getReplicationFactor())
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(properties.getRetentionMs()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                        Integer.toString(properties.getMinInSyncReplicas()))
                .build();
    }
}
