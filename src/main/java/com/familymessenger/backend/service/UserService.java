package com.familymessenger.backend.service;

import com.familymessenger.backend.dto.AuthRequest;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис для работы с пользователями
 * Service for user operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Регистрация нового пользователя
     * Register new user
     *
     * @param request - данные для регистрации / registration data
     * @return созданный пользователь / created user
     */
    @Transactional
    public User registerNewUser(AuthRequest request) {

        log.debug("Creating new user with username: {}", request.getUsername());

        // Создаём нового пользователя
        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        // Шифруем пароль перед сохранением
        // Encrypt password before saving
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Устанавливаем статус ONLINE (по умолчанию)
        // Set status ONLINE (by default)
        user.setStatus(User.UserStatus.ONLINE);

        // Сохраняем в базу данных
        // Save to database
        User savedUser = userRepository.save(user);

        log.info("User created with id: {}", savedUser.getId());

        return savedUser;
    }

    /**
     * Проверка существования пользователя по username
     * Check if user exists by username
     *
     * @param username - имя пользователя / username
     * @return true если существует / true if exists
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Проверка существования пользователя по email
     * Check if user exists by email
     *
     * @param email - email пользователя / user email
     * @return true если существует / true if exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Поиск пользователя по username
     * Find user by username
     *
     * @param username - имя пользователя / username
     * @return пользователь / user
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found with username: {}", username);
                    return new RuntimeException("User not found: " + username);
                });
    }

    /**
     * Поиск пользователя по id
     * Find user by id
     *
     * @param id - идентификатор пользователя / user id
     * @return пользователь / user
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("User not found with id: {}", id);
                    return new RuntimeException("User not found with id: " + id);
                });
    }

    /**
     * Поиск пользователей по имени или email (исключая текущего)
     * Search users by username or email (excluding current user)
     * @param query - поисковый запрос / search query
     * @param currentUserId - ID текущего пользователя / current user ID
     * @return список пользователей / list of users
     */
    public List<User> searchUsers(String query, Long currentUserId) {
        // Минимум 2 символа - и для юзабельности (короче не имеет смысла), и чтобы
        // усложнить перебор всей базы пользователей по одному символу за раз
        // At least 2 characters - both for usability (shorter isn't meaningful) and to
        // make it harder to enumerate the whole user base one character at a time
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return userRepository.searchUsers(query, currentUserId);
    }

    /**
     * Обновление ссылки на аватар пользователя
     * Update the user's avatar URL
     *
     * @param userId - идентификатор пользователя / user id
     * @param avatarUrl - публичная ссылка на файл аватара / public avatar file URL
     * @return обновлённый пользователь / updated user
     */
    @Transactional
    public User updateAvatar(Long userId, String avatarUrl) {
        User user = findById(userId);
        user.setAvatarUrl(avatarUrl);
        User saved = userRepository.save(user);
        log.info("Avatar updated for user: {}", saved.getUsername());
        return saved;
    }

    /**
     * Поиск пользователя по email
     * Find user by email
     *
     * @param email - email пользователя / user email
     * @return пользователь, если найден / user, if found
     */
    public java.util.Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Установка нового пароля (используется при сбросе пароля)
     * Set a new password (used during password reset)
     *
     * @param user - пользователь / user
     * @param rawNewPassword - новый пароль в открытом виде / new plaintext password
     */
    @Transactional
    public void updatePassword(User user, String rawNewPassword) {
        user.setPassword(passwordEncoder.encode(rawNewPassword));
        userRepository.save(user);
        log.info("Password updated for user: {}", user.getUsername());
    }
}