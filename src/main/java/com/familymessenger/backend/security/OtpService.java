package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Одноразовые коды подтверждения входа (2FA по email), хранятся в Redis с TTL.
 * One-time login verification codes (email 2FA), stored in Redis with a TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:otp:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private String key(String username) {
        return KEY_PREFIX + username.toLowerCase();
    }

    /**
     * Генерирует новый 6-значный код, сохраняет его в Redis и возвращает
     * Generates a new 6-digit code, stores it in Redis and returns it
     */
    public String generateAndStore(String username) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(username), code, OTP_TTL);
        return code;
    }

    /**
     * Проверяет код; при совпадении сразу удаляет его (одноразовый)
     * Verifies the code; deletes it immediately on match (single use)
     */
    public boolean verify(String username, String code) {
        if (code == null) {
            return false;
        }
        String stored = redisTemplate.opsForValue().get(key(username));
        boolean matches = stored != null && stored.equals(code.trim());
        if (matches) {
            redisTemplate.delete(key(username));
        }
        return matches;
    }
}
