package com.cheeseocean.im.common.core.queue.config;

import com.cheeseocean.im.common.core.config.CommonJacksonConfig;
import com.cheeseocean.im.common.core.config.CommonKafkaStringConfig;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.chronicle.ChronicleQueueAdapter;
import com.cheeseocean.im.common.core.queue.kafka.KafkaQueueAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class QueueAutoConfigurerTest {

    @Test
    void shouldCreateChronicleAdapterByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(CommonJacksonConfig.class, QueueAutoConfigurer.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueAdapter.class);
                    assertThat(context).hasSingleBean(ChronicleQueueAdapter.class);
                });
    }

    @Test
    void shouldCreateKafkaAdapterWhenQueueTypeIsKafka() {
        new ApplicationContextRunner()
                .withPropertyValues("cheeseim.queue.type=kafka", "spring.kafka.bootstrap-servers=localhost:9092")
                .withUserConfiguration(CommonJacksonConfig.class, CommonKafkaStringConfig.class, QueueAutoConfigurer.class, KafkaProperties.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(QueueAdapter.class);
                    assertThat(context).hasSingleBean(KafkaQueueAdapter.class);
                });
    }
}
