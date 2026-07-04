package com.example.aigateway.ratelimit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class FixedWindowRateLimiterTest {

    @Test
    void checkShouldAllowWhenCountIsWithinLimit() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("60"), eq("2"))).thenReturn(2L);
        FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(redisTemplate, properties());

        RateLimitDecision decision = rateLimiter.check("apiKey", "10", 2, 60);

        assertTrue(decision.allowed());
    }

    @Test
    void checkShouldBlockWhenCountExceedsLimit() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("60"), eq("2"))).thenReturn(3L);
        FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(redisTemplate, properties());

        RateLimitDecision decision = rateLimiter.check("apiKey", "10", 2, 60);

        assertFalse(decision.allowed());
    }

    @Test
    void buildKeyShouldIncludeDimensionIdentifierWindowAndBucket() {
        FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(
                org.mockito.Mockito.mock(StringRedisTemplate.class),
                properties()
        );

        String key = rateLimiter.buildKey("ip", "127.0.0.1", 60);

        assertTrue(key.startsWith("rl:fixed:ip:127.0.0.1:60s:"));
    }

    private GatewayRateLimitProperties properties() {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        properties.setFailOpen(false);
        return properties;
    }
}
