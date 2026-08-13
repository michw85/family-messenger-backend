package com.familymessenger.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void tryAcquire_allowsTheFirstCallAndSetsTheWindowExpiry() {
        when(valueOperations.increment("ratelimit:register:ip:1.2.3.4")).thenReturn(1L);

        assertTrue(rateLimiterService.tryAcquire("register:ip:1.2.3.4", 5, Duration.ofMinutes(10)));
        verify(redisTemplate).expire(eq("ratelimit:register:ip:1.2.3.4"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void tryAcquire_allowsExactlyAtTheLimit() {
        when(valueOperations.increment("ratelimit:register:ip:1.2.3.4")).thenReturn(5L);

        assertTrue(rateLimiterService.tryAcquire("register:ip:1.2.3.4", 5, Duration.ofMinutes(10)));
    }

    @Test
    void tryAcquire_deniesOnceOverTheLimit() {
        when(valueOperations.increment("ratelimit:register:ip:1.2.3.4")).thenReturn(6L);

        assertFalse(rateLimiterService.tryAcquire("register:ip:1.2.3.4", 5, Duration.ofMinutes(10)));
    }

    @Test
    void tryAcquire_doesNotResetTheWindowOnLaterCalls() {
        when(valueOperations.increment("ratelimit:register:ip:1.2.3.4")).thenReturn(2L);

        rateLimiterService.tryAcquire("register:ip:1.2.3.4", 5, Duration.ofMinutes(10));

        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    void tryAcquire_failsOpenWhenRedisIsUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertTrue(rateLimiterService.tryAcquire("register:ip:1.2.3.4", 5, Duration.ofMinutes(10)));
    }
}
