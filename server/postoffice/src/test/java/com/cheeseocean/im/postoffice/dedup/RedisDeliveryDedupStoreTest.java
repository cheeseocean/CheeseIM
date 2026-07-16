package com.cheeseocean.im.postoffice.dedup;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisDeliveryDedupStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void claimShouldMapAtomicScriptResults() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(1L, 3L, 2L);
        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redis, 600, 30);

        DeliveryDedupStore.Claim acquired = store.claim("m1", "u1", "c1");

        assertThat(acquired.status()).isEqualTo(DeliveryDedupStore.ClaimStatus.ACQUIRED);
        assertThat(acquired.key()).contains("m1").contains("u1").contains("c1");
        assertThat(acquired.token()).isNotBlank();
        assertThat(store.claim("m1", "u1", "c1").status())
                .isEqualTo(DeliveryDedupStore.ClaimStatus.IN_PROGRESS);
        assertThat(store.claim("m1", "u1", "c1").status())
                .isEqualTo(DeliveryDedupStore.ClaimStatus.DELIVERED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void commitAndAbortShouldRequireMatchingClaimToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(1L);
        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redis, 600, 30);
        DeliveryDedupStore.Claim claim = store.claim("m1", "u1", "c1");

        assertThat(store.commit(claim)).isTrue();
        assertThat(store.abort(claim)).isTrue();
        assertThat(store.commit(DeliveryDedupStore.Claim.status(DeliveryDedupStore.ClaimStatus.DELIVERED))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureShouldBeUnavailableInsteadOfDuplicateSuccess() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        DeliveryDedupStore.Claim claim = new RedisDeliveryDedupStore(redis, 600, 30)
                .claim("m1", "u1", "c1");

        assertThat(claim.status()).isEqualTo(DeliveryDedupStore.ClaimStatus.UNAVAILABLE);
    }

    @Test
    void invalidIdentityShouldFailClosed() {
        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(mock(StringRedisTemplate.class), 600, 30);

        assertThat(store.claim(null, "u1", "c1").status())
                .isEqualTo(DeliveryDedupStore.ClaimStatus.UNAVAILABLE);
        assertThat(store.claim("m1", null, "c1").status())
                .isEqualTo(DeliveryDedupStore.ClaimStatus.UNAVAILABLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void concurrentClaimsShouldHaveSingleWinner() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AtomicBoolean claimed = new AtomicBoolean();
        when(redis.execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                .thenAnswer(ignored -> claimed.compareAndSet(false, true) ? 1L : 3L);
        RedisDeliveryDedupStore store = new RedisDeliveryDedupStore(redis, 600, 30);

        CompletableFuture<DeliveryDedupStore.ClaimStatus> first = CompletableFuture.supplyAsync(
                () -> store.claim("m1", "u1", "c1").status());
        CompletableFuture<DeliveryDedupStore.ClaimStatus> second = CompletableFuture.supplyAsync(
                () -> store.claim("m1", "u1", "c1").status());

        assertThat(List.of(first.join(), second.join()))
                .containsExactlyInAnyOrder(DeliveryDedupStore.ClaimStatus.ACQUIRED,
                        DeliveryDedupStore.ClaimStatus.IN_PROGRESS);
    }
}
