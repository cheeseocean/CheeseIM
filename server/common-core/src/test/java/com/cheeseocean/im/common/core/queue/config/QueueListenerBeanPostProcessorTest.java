package com.cheeseocean.im.common.core.queue.config;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.queue.processor.QueueListenerBeanPostProcessor;
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

    record DemoPayload(String value) {
    }
}
