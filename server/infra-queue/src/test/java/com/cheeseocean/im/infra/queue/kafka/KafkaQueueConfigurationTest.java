package com.cheeseocean.im.infra.queue.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka 生产者事务标识配置测试。
 */
class KafkaQueueConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaQueueConfiguration.class)
            .withPropertyValues(
                    "spring.application.name=postmaster",
                    "cheeseim.queue.type=kafka");

    @Test
    void shouldGenerateInstanceUniqueTransactionPrefixByDefault() {
        contextRunner.run(first -> {
            String firstPrefix = transactionPrefix(first.getBean(ProducerFactory.class));
            assertThat(firstPrefix).startsWith("postmaster-").endsWith("-queue-");

            contextRunner.run(second -> assertThat(transactionPrefix(second.getBean(ProducerFactory.class)))
                    .isNotEqualTo(firstPrefix));
        });
    }

    @Test
    void shouldHonorExplicitTransactionPrefix() {
        contextRunner.withPropertyValues("cheeseim.queue.kafka.transaction-id-prefix=node-a-queue-")
                .run(context -> assertThat(transactionPrefix(context.getBean(ProducerFactory.class)))
                        .isEqualTo("node-a-queue-"));
    }

    private String transactionPrefix(ProducerFactory<?, ?> producerFactory) {
        return ((DefaultKafkaProducerFactory<?, ?>) producerFactory).getTransactionIdPrefix();
    }
}
