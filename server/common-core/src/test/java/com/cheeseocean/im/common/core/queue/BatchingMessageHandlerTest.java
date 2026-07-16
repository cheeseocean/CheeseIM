package com.cheeseocean.im.common.core.queue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchingMessageHandlerTest {

    @Test
    void shouldNotReturnBeforeDelegateCompletes() throws Exception {
        CountDownLatch delegateEntered = new CountDownLatch(1);
        CountDownLatch releaseDelegate = new CountDownLatch(1);
        BatchingMessageHandler<String> handler = new BatchingMessageHandler<>(1, 100, 1, messages -> {
            delegateEntered.countDown();
            await(releaseDelegate);
        });

        CompletableFuture<Void> handling = CompletableFuture.runAsync(
                () -> handler.handle(new KeyedMessage<>("conversation-1", "m1")));
        assertThat(delegateEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(handling).isNotDone();

        releaseDelegate.countDown();
        handling.get(1, TimeUnit.SECONDS);
        handler.stop();
    }

    @Test
    void shouldNeverPassMoreThanConfiguredBatchSize() {
        List<Integer> observedBatchSizes = new CopyOnWriteArrayList<>();
        BatchingMessageHandler<Integer> handler = new BatchingMessageHandler<>(3, 50, 2,
                messages -> observedBatchSizes.add(messages.size()));

        List<CompletableFuture<Void>> calls = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> CompletableFuture.runAsync(
                        () -> handler.handle(new KeyedMessage<>("same-key", index))))
                .toList();
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        handler.stop();

        assertThat(observedBatchSizes).isNotEmpty().allMatch(size -> size <= 3);
        assertThat(observedBatchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
    }

    @Test
    void shouldPropagateDelegateFailureToCaller() {
        BatchingMessageHandler<String> handler = new BatchingMessageHandler<>(1, 50, 1,
                messages -> {
                    throw new IllegalArgumentException("business failed");
                });

        assertThatThrownBy(() -> handler.handle(new KeyedMessage<>("conversation-1", "m1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("business failed");
        handler.stop();
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new BatchingMessageHandler<>(0, 100, 1, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BatchingMessageHandler<>(1, 0, 1, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stopShouldWaitForInFlightCompletion() throws Exception {
        CountDownLatch delegateEntered = new CountDownLatch(1);
        CountDownLatch releaseDelegate = new CountDownLatch(1);
        BatchingMessageHandler<String> handler = new BatchingMessageHandler<>(1, 50, 1, messages -> {
            delegateEntered.countDown();
            await(releaseDelegate);
        });
        CompletableFuture<Void> handling = CompletableFuture.runAsync(
                () -> handler.handle(new KeyedMessage<>("conversation-1", "m1")));
        assertThat(delegateEntered.await(1, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> stopping = CompletableFuture.runAsync(handler::stop);
        assertThat(stopping).isNotDone();
        assertThat(handling).isNotDone();
        releaseDelegate.countDown();

        stopping.get(1, TimeUnit.SECONDS);
        handling.get(1, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
