package com.cheeseocean.im.common.core.queue.kafka;

import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class KafkaQueueAdapterTest {

    @Test
    void shouldWaitForBrokerAcknowledgment() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, byte[]>> result = new CompletableFuture<>();
        when(template.send("topic", "key", new byte[]{1})).thenReturn(result);
        KafkaQueueAdapter adapter = adapter(template);

        CompletableFuture<Void> sending = CompletableFuture.runAsync(
                () -> adapter.send("topic", "key", new byte[]{1}));
        org.assertj.core.api.Assertions.assertThat(sending).isNotDone();

        result.complete(mock(SendResult.class));
        sending.join();
    }

    @Test
    void shouldExposeAsynchronousBrokerFailure() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, byte[]>> result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(template.send("topic", "key", new byte[]{1})).thenReturn(result);

        assertThatThrownBy(() -> adapter(template).send("topic", "key", new byte[]{1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broker rejected")
                .hasRootCauseMessage("broker unavailable");
    }

    @Test
    void shouldWaitForEveryMessageInBatch() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send("topic", "k1", new byte[]{1}))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(template.send("topic", "k2", new byte[]{2}))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        adapter(template).sendBatch("topic", List.of(
                new KeyedMessage<>("k1", new byte[]{1}),
                new KeyedMessage<>("k2", new byte[]{2})));

        verify(template).send("topic", "k1", new byte[]{1});
        verify(template).send("topic", "k2", new byte[]{2});
        verify(template).executeInTransaction(any());
    }

    @Test
    void shouldFailWholeTransactionWhenAnyBatchRecordIsRejected() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send("topic", "k1", new byte[]{1}))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        CompletableFuture<SendResult<String, byte[]>> rejected = new CompletableFuture<>();
        rejected.completeExceptionally(new IllegalStateException("second rejected"));
        when(template.send("topic", "k2", new byte[]{2})).thenReturn(rejected);

        assertThatThrownBy(() -> adapter(template).sendBatch("topic", List.of(
                new KeyedMessage<>("k1", new byte[]{1}),
                new KeyedMessage<>("k2", new byte[]{2}))))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("second rejected");

        verify(template).executeInTransaction(any());
    }

    @Test
    void shouldCommitOnlyAfterEachRecordHandlerReturns() {
        ContainerProperties properties = new ContainerProperties("topic");

        adapter(mock(KafkaTemplate.class)).configureReliableAcknowledgment(properties);

        org.assertj.core.api.Assertions.assertThat(properties.getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        org.assertj.core.api.Assertions.assertThat(properties.isSyncCommits()).isTrue();
    }

    @Test
    void shouldInstallRetryAndDltHandlerForConsumers() {
        ConcurrentMessageListenerContainer<String, byte[]> container = mock(ConcurrentMessageListenerContainer.class);

        adapter(mock(KafkaTemplate.class)).configureFailureHandling(container);

        verify(container).setCommonErrorHandler(org.mockito.ArgumentMatchers.isA(DefaultErrorHandler.class));
    }

    @Test
    void shouldRouteFailedSingleAndBatchRecordsToSourceTopicDltPartition() {
        KafkaQueueAdapter adapter = adapter(mock(KafkaTemplate.class));
        org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> single =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("ingress", 2, 1L, "k", new byte[]{1});
        org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> batchRecord =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("delivery", 4, 2L, "k", new byte[]{2});

        org.assertj.core.api.Assertions.assertThat(adapter.dltDestination(single, new IllegalStateException()))
                .isEqualTo(new org.apache.kafka.common.TopicPartition("ingress.DLT", 2));
        org.assertj.core.api.Assertions.assertThat(adapter.dltDestination(batchRecord, new IllegalStateException()))
                .isEqualTo(new org.apache.kafka.common.TopicPartition("delivery.DLT", 4));
        org.assertj.core.api.Assertions.assertThat(adapter.createErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void shouldPublishFailedSingleRecordToDltBeforeRecovery() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send(org.mockito.ArgumentMatchers.any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        KafkaQueueAdapter adapter = adapter(template);
        DefaultErrorHandler handler = adapter.createErrorHandler(new org.springframework.util.backoff.FixedBackOff(0, 0));
        org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record =
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("ingress", 1, 3L, "k", new byte[]{1});

        boolean recovered = handler.handleOne(new IllegalStateException("poison"), record,
                mock(org.apache.kafka.clients.consumer.Consumer.class),
                mock(org.springframework.kafka.listener.MessageListenerContainer.class));

        org.assertj.core.api.Assertions.assertThat(recovered).isTrue();
        org.mockito.ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, byte[]>> captor =
                org.mockito.ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        verify(template).send(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().topic()).isEqualTo("ingress.DLT");
    }

    @Test
    void shouldPublishEveryFailedBatchRecordToDlt() {
        KafkaTemplate<String, byte[]> template = mock(KafkaTemplate.class);
        when(template.send(org.mockito.ArgumentMatchers.any(org.apache.kafka.clients.producer.ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        DefaultErrorHandler handler = adapter(template)
                .createErrorHandler(new org.springframework.util.backoff.FixedBackOff(0, 0));
        org.apache.kafka.common.TopicPartition partition = new org.apache.kafka.common.TopicPartition("delivery", 0);
        List<org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]>> records = List.of(
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("delivery", 0, 1L, "k1", new byte[]{1}),
                new org.apache.kafka.clients.consumer.ConsumerRecord<>("delivery", 0, 2L, "k2", new byte[]{2}));
        org.apache.kafka.clients.consumer.ConsumerRecords<String, byte[]> batch =
                new org.apache.kafka.clients.consumer.ConsumerRecords<>(Map.of(partition, records));
        org.springframework.kafka.listener.MessageListenerContainer container =
                mock(org.springframework.kafka.listener.MessageListenerContainer.class);
        when(container.getContainerProperties()).thenReturn(new ContainerProperties("delivery"));

        handler.handleBatch(new IllegalStateException("batch poison"), batch,
                mock(org.apache.kafka.clients.consumer.Consumer.class), container, () -> { });

        org.mockito.ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord<String, byte[]>> captor =
                org.mockito.ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
        verify(template, org.mockito.Mockito.times(2)).send(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getAllValues())
                .allMatch(record -> record.topic().equals("delivery.DLT"));
    }

    private KafkaQueueAdapter adapter(KafkaTemplate<String, byte[]> template) {
        doAnswer(invocation -> {
            org.springframework.kafka.core.KafkaOperations.OperationsCallback<String, byte[], Object> callback =
                    invocation.getArgument(0);
            return callback.doInOperations(template);
        }).when(template).executeInTransaction(any());
        return new KafkaQueueAdapter(template, new ObjectMapper(), new KafkaProperties());
    }
}
