package com.familymessenger.backend.security;

import com.familymessenger.backend.entity.RefreshToken;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Сервис выдачи и валидации refresh-токенов.
 * Access-токен живёт недолго (см. jwt.expiration), а refresh-токен позволяет
 * получить новый access-токен без повторного ввода пароля, пока не истёк
 * (jwt.refresh-expiration) или не был отозван (logout).
 * Refresh token issuance and validation.
 * The access token is short-lived (jwt.expiration); the refresh token lets
 * the client obtain a new access token without re-entering the password,
 * until it expires (jwt.refresh-expiration) or is revoked (logout).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Выдаёт новый refresh-токен пользователю и сохраняет его хэш в БД.
     * Issues a new refresh token for the user and stores its hash in the DB.
     *
     * @return сырой (не хэшированный) токен для отправки клиенту / raw token to send to the client
     */
    @Transactional
    public String issueRefreshToken(User user) {
        String raw = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(raw));
        refreshToken.setExpiryDate(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)));
        refreshTokenRepository.save(refreshToken);

        return raw;
    }

    /**
     * Проверяет refresh-токен и сразу отзывает его (ротация — токен одноразовый).
     * Validates the refresh token and immediately revokes it (rotation — single use).
     *
     * @throws IllegalArgumentException если токен неверный, истёк или уже отозван / if invalid, expired or already revoked
     */
    @Transactional
    public User validateAndConsume(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return refreshToken.getUser();
    }

    /**
     * Отзывает refresh-токен (logout)
     * Revokes a refresh token (logout)
     */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    /**
     * Отзывает все активные refresh-токены пользователя (например, после
     * сброса пароля - чтобы все ранее выданные сессии перестали работать).
     * Revokes all of the user's active refresh tokens (e.g. after a
     * password reset - so every previously issued session stops working).
     */
    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.findByUserAndRevokedFalse(user)
                .forEach(rt -> rt.setRevoked(true));
    }
}
