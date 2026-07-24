package com.familymessenger.backend.service;

import com.familymessenger.backend.dto.TurnCredentialsDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Генерация временных TURN-учётных данных для coturn по стандартной схеме
 * TURN REST API (use-auth-secret): username = "{expiryEpoch}:{login}",
 * credential = base64(HMAC-SHA1(secret, username)). Общий секрет никогда не
 * попадает в мобильное приложение.
 * Generates short-lived TURN credentials for coturn per the standard TURN
 * REST API scheme (use-auth-secret): username = "{expiryEpoch}:{login}",
 * credential = base64(HMAC-SHA1(secret, username)). The shared secret never
 * reaches the mobile app.
 */
@Service
public class TurnCredentialService {

    private static final String HMAC_ALGO = "HmacSHA1";

    @Value("${turn.shared-secret}")
    private String sharedSecret;

    @Value("${turn.ttl-seconds:3600}")
    private long ttlSeconds;

    @Value("${turn.uris}")
    private String turnUris;

    public TurnCredentialsDto generate(String username) {
        long expiry = Instant.now().getEpochSecond() + ttlSeconds;
        String turnUsername = expiry + ":" + username;
        String credential = hmacSha1Base64(sharedSecret, turnUsername);

        List<String> urls = Arrays.asList(turnUris.split(","));

        return TurnCredentialsDto.builder()
                .username(turnUsername)
                .credential(credential)
                .ttlSeconds(ttlSeconds)
                .urls(urls)
                .build();
    }

    private static String hmacSha1Base64(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TURN credential", e);
        }
    }
}
