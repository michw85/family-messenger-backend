package com.familymessenger.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.OFFLINE;

    private LocalDateTime lastSeen;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "fcm_token")
    private String fcmToken;

    /**
     * Глобальная роль владельца приложения - может удалять любой чат (в т.ч.
     * чужие блокноты) и блокировать вход другим пользователям. Нет UI для
     * назначения - выставляется вручную в БД один раз для реального владельца.
     * The app owner's global role - can delete any chat (including other
     * users' notebooks) and block other users' login. No UI to assign it -
     * set manually in the DB once for the real owner.
     */
    @Column(name = "is_superadmin", nullable = false, columnDefinition = "boolean default false")
    private boolean isSuperadmin = false;

    /**
     * Заблокирован суперадмином - см. isEnabled() ниже, Spring Security сам
     * откажет в логине такому пользователю.
     * Blacklisted by a superadmin - see isEnabled() below, Spring Security
     * itself refuses login for such a user.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blacklisted = false;

    /**
     * Подтверждён ли суперадмином вход этому пользователю. Новые регистрации
     * создаются с approved=false (см. UserService.registerNewUser) - защита
     * от того, что случайные люди, скачавшие приложение (например, с
     * закрытого трека Google Play), смогут им пользоваться без ведома
     * владельца. columnDefinition default TRUE - иначе при авто-миграции
     * схемы уже существующие (и так пользующиеся приложением) пользователи
     * внезапно потеряли бы доступ.
     * Whether a superadmin has approved this user's login. New registrations
     * are created with approved=false (see UserService.registerNewUser) -
     * guards against random people who got hold of the app (e.g. via a
     * closed Google Play testing track) being able to use it without the
     * owner's knowledge. columnDefinition defaults to TRUE - otherwise the
     * schema auto-migration would suddenly lock out everyone already using
     * the app.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean approved = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastSeen = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // UserDetails methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return !blacklisted && approved; }

    public enum UserStatus {
        ONLINE, OFFLINE, AWAY
    }
}