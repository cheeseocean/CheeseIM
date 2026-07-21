package com.cheeseocean.im.infra.queue.config;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.infra.queue.processor.QueueListenerBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class QueueListenerBeanPostProcessorTest {

    @Test
    void shouldSubscribeAnnotatedMethodUsingPayloadParameterType() {
        TestQueueAdapter adapter = new TestQueueAdapter();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(QueueAdapter.class, () -> adapter);
            context.registerBean(QueueListenerBeanPostProcessor.class);
            context.registerBean(TestConsumer.class);
            context.refresh();

            adapter.dispatch("topic-a", new DemoPayload("v1"));

            assertThat(context.getBean(TestConsumer.class).received()).containsExactly("v1");
        }
        assertThat(adapter.activeSubscriptionCount()).isZero();
    }

    @Test
    void shouldUseNativeBatchSubscriptionAndPreserveKeyGroups() {
        TestQueueAdapter adapter = new TestQueueAdapter();

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(QueueAdapter.class, () -> adapter);
            context.registerBean(QueueListenerBeanPostProcessor.class);
            context.registerBean(TestBatchConsumer.class);
            context.refresh();

            adapter.dispatchBatch("topic-b", List.of(
                    new com.cheeseocean.im.common.core.queue.KeyedMessage<>("c1", new DemoPayload("v1")),
                    new com.cheeseocean.im.common.core.queue.KeyedMessage<>("c1", new DemoPayload("v2")),
                    new com.cheeseocean.im.common.core.queue.KeyedMessage<>("c2", new DemoPayload("v3"))));

            assertThat(context.getBean(TestBatchConsumer.class).received())
                    .containsExactly(List.of("v1", "v2"), List.of("v3"));
        }
    }

    static final class TestQueueAdapter implements QueueAdapter {
        private final List<SubscriptionRequest> subscriptions = new CopyOnWriteArrayList<>();

        @Override
        public void send(String topic, String key, byte[] message) {
            for (SubscriptionRequest subscription : subscriptions) {
                if (subscription.topic().equals(topic)) {
                    // This test uses a simple dispatch without deserialization
                }
            }
        }

        @Override
        public <T> Subscription subscribe(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<T> handler) {
            subscriptions.add(new SubscriptionRequest(topic, group, concurrency, payloadType, handler));
            return () -> subscriptions.removeIf(subscription -> subscription.handler() == handler);
        }

        @Override
        public <T> Subscription subscribeBatch(String topic, String group, int concurrency, int batchSize,
                                               long batchIntervalMs, Class<T> payloadType,
                                               QueueMessageHandler<List<com.cheeseocean.im.common.core.queue.KeyedMessage<T>>> handler) {
            subscriptions.add(new SubscriptionRequest(topic, group, concurrency, payloadType, handler));
            return () -> subscriptions.removeIf(subscription -> subscription.handler() == handler);
        }

        void dispatch(String topic, DemoPayload payload) {
            // 直接调用 handler，不走 send 方法
            for (SubscriptionRequest subscription : subscriptions) {
                if (subscription.topic().equals(topic)) {
                    @SuppressWarnings("unchecked")
                    QueueMessageHandler<DemoPayload> handler = (QueueMessageHandler<DemoPayload>) subscription.handler();
                    handler.handle(payload);
                }
            }
        }

        void dispatchBatch(String topic, List<com.cheeseocean.im.common.core.queue.KeyedMessage<DemoPayload>> payloads) {
            for (SubscriptionRequest subscription : subscriptions) {
                if (subscription.topic().equals(topic)) {
                    @SuppressWarnings("unchecked")
                    QueueMessageHandler<List<com.cheeseocean.im.common.core.queue.KeyedMessage<DemoPayload>>> handler =
                            (QueueMessageHandler<List<com.cheeseocean.im.common.core.queue.KeyedMessage<DemoPayload>>>) subscription.handler();
                    handler.handle(payloads);
                }
            }
        }

        int activeSubscriptionCount() {
            return subscriptions.size();
        }
    }

    record SubscriptionRequest(String topic, String group, int concurrency, Class<?> payloadType, QueueMessageHandler<?> handler) {
    }

    static final class TestConsumer {
        private final List<String> received = new CopyOnWriteArrayList<>();

        @QueueListener(topic = "topic-a", group = "g1", concurrency = 1)
        public void handle(DemoPayload payload) {
            received.add(payload.value());
        }

        List<String> received() {
            return received;
        }
    }

    static final class TestBatchConsumer {
        private final List<List<String>> received = new CopyOnWriteArrayList<>();

        @QueueListener(topic = "topic-b", group = "g2", concurrency = 2, batch = true, batchSize = 3)
        public void consume(List<DemoPayload> payloads) {
            received.add(payloads.stream().map(DemoPayload::value).toList());
        }

        List<List<String>> received() {
            return received;
        }
    }

    record DemoPayload(String value) {
    }
}
