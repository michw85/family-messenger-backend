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
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisTemplate);
    }

    @Test
    void isBlocked_falseWhenNoAttemptsRecordedYet() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:someuser")).thenReturn(null);

        assertFalse(loginAttemptService.isBlocked("someuser"));
    }

    @Test
    void isBlocked_falseBelowTheLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:someuser")).thenReturn("4");

        assertFalse(loginAttemptService.isBlocked("someuser"));
    }

    @Test
    void isBlocked_trueOnceTheLimitIsReached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:someuser")).thenReturn("5");

        assertTrue(loginAttemptService.isBlocked("someuser"));
    }

    @Test
    void isBlocked_usernameKeyIsCaseInsensitive() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:attempts:someuser")).thenReturn("5");

        assertTrue(loginAttemptService.isBlocked("SomeUser"));
    }

    @Test
    void isBlocked_failsOpenWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> assertFalse(loginAttemptService.isBlocked("someuser")));
    }

    @Test
    void recordFailedAttempt_setsExpiryOnlyOnTheFirstAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:attempts:someuser")).thenReturn(1L);

        loginAttemptService.recordFailedAttempt("someuser");

        verify(redisTemplate).expire(eq("login:attempts:someuser"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void recordFailedAttempt_doesNotResetExpiryOnLaterAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("login:attempts:someuser")).thenReturn(3L);

        loginAttemptService.recordFailedAttempt("someuser");

        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    void recordFailedAttempt_swallowsRedisFailures() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> loginAttemptService.recordFailedAttempt("someuser"));
    }

    @Test
    void resetAttempts_deletesTheAttemptCounterKey() {
        loginAttemptService.resetAttempts("someuser");

        verify(redisTemplate).delete("login:attempts:someuser");
    }

    @Test
    void resetAttempts_swallowsRedisFailures() {
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> loginAttemptService.resetAttempts("someuser"));
    }
}
