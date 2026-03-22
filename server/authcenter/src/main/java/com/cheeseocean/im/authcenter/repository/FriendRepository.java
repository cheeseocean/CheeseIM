package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
@Repository
public class FriendRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public FriendRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void acceptFriendPair(String userId, String friendUserId) {
        redisTemplate.opsForSet().add(friendKey(userId), friendUserId);
        redisTemplate.opsForSet().add(friendKey(friendUserId), userId);
        redisTemplate.opsForSet().remove(requestKey(userId), friendUserId);
    }

    public void addRequest(String userId, String friendUserId) {
        redisTemplate.opsForSet().add(requestKey(friendUserId), userId);
    }

    public boolean areAcceptedFriends(String userId, String friendUserId) {
        Boolean member = redisTemplate.opsForSet().isMember(friendKey(userId), friendUserId);
        return Boolean.TRUE.equals(member);
    }

    public List<String> listFriendIds(String userId) {
        Set<Object> values = redisTemplate.opsForSet().members(friendKey(userId));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(String::valueOf).sorted().toList();
    }

    public List<String> listIncomingRequestIds(String userId) {
        Set<Object> values = redisTemplate.opsForSet().members(requestKey(userId));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(String::valueOf).sorted().toList();
    }

    public boolean hasIncomingRequest(String userId, String friendUserId) {
        Boolean member = redisTemplate.opsForSet().isMember(requestKey(userId), friendUserId);
        return Boolean.TRUE.equals(member);
    }

    private String friendKey(String userId) {
        return RedisKeys.userFriends(userId);
    }

    private String requestKey(String userId) {
        return RedisKeys.userFriendRequests(userId);
    }
}
