package com.cheeseocean.im.common.core.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthRedisKeysTest {

    @Test
    void shouldBuildAuthAndFriendRedisKeys() {
        assertEquals("cheese_im:ws_ticket:t-1", RedisKeys.wsTicket("t-1"));
        assertEquals("cheese_im:user_session:s-1", RedisKeys.userSession("s-1"));
        assertEquals("cheese_im:user_sessions:u-1", RedisKeys.userSessions("u-1"));
        assertEquals("cheese_im:device_session:u-1:d-1", RedisKeys.deviceSession("u-1", "d-1"));
        assertEquals("cheese_im:user_security:u-1", RedisKeys.userSecurity("u-1"));
        assertEquals("cheese_im:user_friends:u-1", RedisKeys.userFriends("u-1"));
        assertEquals("cheese_im:user_friend_requests:u-1", RedisKeys.userFriendRequests("u-1"));
    }
}
