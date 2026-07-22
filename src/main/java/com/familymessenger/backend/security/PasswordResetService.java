package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Одноразовые коды для сброса пароля, хранятся в Redis с TTL - та же схема,
 * что и у OtpService для входа, но в отдельном пространстве ключей (по email,
 * а не по username - забывший пароль может не помнить точный username).
 * One-time password reset codes, stored in Redis with a TTL - same scheme as
 * OtpService for login, but in a separate key namespace (keyed by email, not
 * username - someone who forgot their password may not recall the exact one).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "password:reset:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private String key(String email) {
        return KEY_PREFIX + email.toLowerCase();
    }

    /**
     * Генерирует новый 6-значный код, сохраняет его в Redis и возвращает
     * Generates a new 6-digit code, stores it in Redis and returns it
     */
    public String generateAndStore(String email) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(email), code, RESET_TTL);
        return code;
    }

    /**
     * Проверяет код; при совпадении сразу удаляет его (одноразовый)
     * Verifies the code; deletes it immediately on match (single use)
     */
    public boolean verify(String email, String code) {
        if (code == null) {
            return false;
        }
        String stored = redisTemplate.opsForValue().get(key(email));
        boolean matches = stored != null && stored.equals(code.trim());
        if (matches) {
            redisTemplate.delete(key(email));
        }
        return matches;
    }
}
