package com.familymessenger.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * Провайдер для работы с JWT токенами
 * Provider for JWT token operations
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpiration;

    /**
     * Получение ключа для подписи токена
     * Getting the key for token signing
     */
    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Генерация JWT токена для пользователя
     * Generating JWT token for user
     *
     * @param username - имя пользователя / username
     * @return сгенерированный токен / generated token
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(username)                    // Устанавливаем username как subject / Set username as subject
                .setIssuedAt(now)                        // Время выпуска / Issue time
                .setExpiration(expiryDate)               // Время истечения / Expiration time
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)  // Подпись / Signature
                .compact();
    }

    /**
     * Получение username из токена
     * Getting username from token
     *
     * @param token - JWT токен / JWT token
     * @return username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    /**
     * Валидация токена (проверка подписи и срока действия)
     * Token validation (checking signature and expiration)
     *
     * @param token - JWT токен / JWT token
     * @return true если токен валиден / true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}