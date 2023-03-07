package com.cheeseocean.im.push.consumer;

import com.cheeseocean.im.common.config.IMConfig;
import com.cheeseocean.im.common.consumer.BaseConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

public class PushConsumerService extends Thread implements BaseConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PushConsumerService.class);
    private KafkaConsumer<String, byte[]> consumer;

    @Override
    public void init(IMConfig IMConfig) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "push-service");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer",
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumer = new KafkaConsumer<>(props);
    }

    @Override
    public void run() {
        while (true) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, byte[]> record : records) {

            }
        }
    }
}
