package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Защита от подбора пароля: считает неудачные попытки входа в Redis
 * и временно блокирует логин после превышения лимита.
 * Brute-force protection: tracks failed login attempts in Redis
 * and temporarily blocks login after the limit is exceeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:attempts:";

    private final StringRedisTemplate redisTemplate;

    private String key(String username) {
        return KEY_PREFIX + username.toLowerCase();
    }

    /**
     * @return true, если для данного username превышен лимит неудачных попыток
     * true if the failed-attempt limit has been exceeded for this username
     */
    public boolean isBlocked(String username) {
        try {
            String value = redisTemplate.opsForValue().get(key(username));
            return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
        } catch (Exception e) {
            // Redis недоступен - не блокируем логин, просто не защищаем от брутфорса
            // Redis unavailable - don't block login, just skip brute-force protection
            log.warn("LoginAttemptService: Redis unavailable, failing open: {}", e.getMessage());
            return false;
        }
    }

    public void recordFailedAttempt(String username) {
        try {
            String key = key(username);
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key, LOCK_DURATION);
            }
            log.warn("Failed login attempt #{} for username: {}", attempts, username);
        } catch (Exception e) {
            log.warn("LoginAttemptService: failed to record attempt: {}", e.getMessage());
        }
    }

    public void resetAttempts(String username) {
        try {
            redisTemplate.delete(key(username));
        } catch (Exception e) {
            log.warn("LoginAttemptService: failed to reset attempts: {}", e.getMessage());
        }
    }
}
