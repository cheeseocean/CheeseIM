package com.cheeseocean.im.infra.queue.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;

class KafkaTopicConfigurationTest {

    @Test
    void shouldDeclareMainAndDltTopicsWithProductionContract() {
        KafkaTopicProperties properties = properties();
        NewTopic[] topics = new KafkaTopicConfiguration().topicDeclarations(properties).toArray(NewTopic[]::new);

        assertEquals(8, topics.length);
        assertTrue(Arrays.stream(topics).anyMatch(topic -> topic.name().equals("ingress.DLT")));
        assertTrue(Arrays.stream(topics).allMatch(topic -> topic.numPartitions() == 12));
        assertTrue(Arrays.stream(topics).allMatch(topic -> topic.replicationFactor() == 3));
        assertTrue(Arrays.stream(topics).allMatch(topic -> "2".equals(topic.configs().get("min.insync.replicas"))));
    }

    @Test
    void shouldExposeDeclarationsThroughKafkaAdminNewTopicsBean() {
        assertTrue(new KafkaTopicConfiguration().cheeseImTopics(properties()) instanceof KafkaAdmin.NewTopics);
    }

    @Test
    void clusterShouldRejectDisabledTopicContract() {
        KafkaTopicProperties properties = properties();
        properties.setAutoCreateEnabled(false);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("cheeseim.runtime.mode")).thenReturn("cluster");

        assertThrows(IllegalStateException.class, () -> new KafkaTopicConfiguration()
                .kafkaTopicContractValidator(properties, environment, mock(Admin.class))
                .run(mock(ApplicationArguments.class)));
    }

    @Test
    void clusterValidationShouldRejectActualPartitionDrift() {
        KafkaTopicConfiguration configuration = new KafkaTopicConfiguration();
        KafkaTopicProperties properties = properties();
        Admin admin = mock(Admin.class);
        org.apache.kafka.clients.admin.DescribeTopicsResult result =
                mock(org.apache.kafka.clients.admin.DescribeTopicsResult.class);
        Map<String, org.apache.kafka.clients.admin.TopicDescription> drifted = configuration
                .topicDeclarations(properties).stream().collect(Collectors.toMap(NewTopic::name,
                        topic -> new org.apache.kafka.clients.admin.TopicDescription(
                                topic.name(), false, java.util.List.of())));
        when(admin.describeTopics(anyCollection())).thenReturn(result);
        when(result.allTopicNames()).thenReturn(org.apache.kafka.common.KafkaFuture.completedFuture(drifted));

        assertThrows(IllegalStateException.class,
                () -> configuration.validateActualTopics(admin, properties));
    }

    private static KafkaTopicProperties properties() {
        KafkaTopicProperties properties = new KafkaTopicProperties();
        properties.setAutoCreateEnabled(true);
        return properties;
    }
}
