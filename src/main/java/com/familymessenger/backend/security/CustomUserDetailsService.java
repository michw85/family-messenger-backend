package com.familymessenger.backend.security;

import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Сервис для загрузки пользователя из базы данных для Spring Security
 * Service for loading user from database for Spring Security
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Загружает пользователя по username или email для аутентификации
     * Loads user by username or email for authentication
     *
     * @param username - имя пользователя или email / username or email
     * @return UserDetails объект для Spring Security / UserDetails object for Spring Security
     * @throws UsernameNotFoundException если пользователь не найден / if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Ищем пользователя по username или email
        // Find user by username or email
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> {
                    log.error("User not found with username/email: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        log.debug("User loaded: {} with role USER", user.getUsername());

        // Создаём список ролей (пока только USER, потом можно добавить ADMIN)
        // Create list of authorities (currently only USER, ADMIN can be added later)
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        // Возвращаем объект UserDetails для Spring Security
        // Return UserDetails object for Spring Security
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),    // username для аутентификации / username for authentication
                user.getPassword(),    // зашифрованный пароль / encrypted password
                authorities            // права и роли / roles and authorities
        );
    }
}