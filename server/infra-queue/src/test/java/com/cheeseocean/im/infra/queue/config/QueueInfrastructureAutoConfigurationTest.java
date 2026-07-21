package com.cheeseocean.im.infra.queue.config;

import com.cheeseocean.im.common.core.config.CommonJacksonConfig;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.infra.queue.chronicle.ChronicleQueueAdapter;
import com.cheeseocean.im.infra.queue.kafka.KafkaQueueAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QueueInfrastructureAutoConfigurationTest {

    @Test
    void shouldCreateChronicleAdapterByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(CommonJacksonConfig.class, QueueInfrastructureAutoConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueAdapter.class);
                    assertThat(context).hasSingleBean(ChronicleQueueAdapter.class);
                });
    }

    @Test
    void shouldCreateKafkaAdapterWhenQueueTypeIsKafka() {
        new ApplicationContextRunner()
                .withPropertyValues("cheeseim.queue.type=kafka", "spring.kafka.bootstrap-servers=localhost:9092")
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .withUserConfiguration(CommonJacksonConfig.class, QueueInfrastructureAutoConfiguration.class, KafkaProperties.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueAdapter.class);
                    assertThat(context).hasSingleBean(KafkaQueueAdapter.class);
                });
    }
}
