package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Простой генерический rate-limiter на Redis (fixed window): не более
 * maxAttempts обращений с одним и тем же ключом за окно window. Используется
 * для эндпоинтов, которые не защищены LoginAttemptService, но всё равно могут
 * быть проспамлены анонимно (регистрация, запрос сброса пароля).
 * A simple generic Redis-backed rate limiter (fixed window): at most
 * maxAttempts calls with the same key within the window. Used for endpoints
 * not covered by LoginAttemptService but still spammable anonymously
 * (registration, password-reset requests).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    /**
     * @param key         произвольный идентификатор бакета (например "register:ip:1.2.3.4") /
     *                    arbitrary bucket identifier (e.g. "register:ip:1.2.3.4")
     * @param maxAttempts сколько обращений разрешено за окно / how many calls are allowed within the window
     * @param window      длительность окна / window duration
     * @return true, если обращение разрешено (лимит не превышен) / true if the call is allowed (limit not exceeded)
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        try {
            String redisKey = KEY_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            return count == null || count <= maxAttempts;
        } catch (Exception e) {
            // Redis недоступен - не блокируем запрос, просто не защищаем от спама в этот момент
            // Redis unavailable - don't block the request, just skip spam protection for now
            log.warn("RateLimiterService: Redis unavailable, failing open: {}", e.getMessage());
            return true;
        }
    }
}
