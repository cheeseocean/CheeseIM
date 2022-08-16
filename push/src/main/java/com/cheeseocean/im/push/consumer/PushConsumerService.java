package com.cheeseocean.im.push.consumer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheeseocean.im.base.consumer.ConsumerHandler;

public class PushConsumerService implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(PushConsumerService.class);
    private KafkaConsumer<String, byte[]> consumer;
    private List<ConsumerHandler> handlers;

    public PushConsumerService(List<ConsumerHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            throw new RuntimeException("handlers can't be null");
        }
        this.handlers = handlers;
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

    public void run() {
        while (true) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
            for (ConsumerRecord<String, byte[]> record : records) {
                for (ConsumerHandler handler : handlers) {
                    if (handler.canHandle(record.topic(), record.key())) {
                        handler.handle(record.key(), record.value());
                    }
                }
            }
        }
    }
}
