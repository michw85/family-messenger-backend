package com.familymessenger.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh-токен для обновления access-токена без повторного ввода пароля.
 * Хранится только SHA-256 хэш токена, не сырое значение.
 * Refresh token used to obtain a new access token without re-entering the password.
 * Only the SHA-256 hash of the token is stored, never the raw value.
 */
@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private boolean revoked = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
