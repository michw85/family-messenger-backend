package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.UserDto;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.RateLimiterService;
import com.familymessenger.backend.service.FileService;
import com.familymessenger.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для работы с пользователями
 * Controller for user operations
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FileService fileService;
    private final RateLimiterService rateLimiterService;

    /**
     * Поиск пользователей по имени или email (не включая текущего)
     * Search users by username or email (excluding current user)
     *
     * @param query - поисковый запрос / search query
     * @param currentUser - текущий пользователь / current user
     * @return список найденных пользователей / list of found users
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestParam String query,
                                         @AuthenticationPrincipal User currentUser) {
        log.info("Searching users with query: {} by user: {}", query, currentUser.getUsername());

        // Поиск отдаёт email найденных пользователей (нужно на фронте, чтобы отличить
        // тёзок при добавлении в чат) - лимитируем по пользователю, чтобы это нельзя
        // было использовать как скрипт для перебора базы email/username
        // Search returns matched users' email (needed on the frontend to tell namesakes
        // apart when adding them to a chat) - rate-limited per user so it can't be
        // scripted to enumerate the whole email/username database
        if (!rateLimiterService.tryAcquire("user-search:" + currentUser.getId(), 60, Duration.ofMinutes(1))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many search requests. Try again later / Слишком много запросов поиска. Попробуйте позже");
        }

        List<User> users = userService.searchUsers(query, currentUser.getId());
        List<UserDto> dtos = users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Загрузка/смена аватара текущего пользователя
     * Upload/change the current user's avatar
     *
     * @param file - файл изображения / image file
     * @param currentUser - текущий пользователь / current user
     * @return обновлённые данные пользователя / updated user data
     */
    @PostMapping("/me/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          @AuthenticationPrincipal User currentUser) {

        log.info("Uploading avatar for user: {}", currentUser.getUsername());

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty / Файл пуст");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed / Разрешены только изображения");
            }

            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size exceeds 10MB / Размер файла превышает 10 МБ");
            }

            String avatarUrl = fileService.uploadFile(file, "avatars", currentUser);
            User updated = userService.updateAvatar(currentUser.getId(), avatarUrl);

            return ResponseEntity.ok(UserDto.fromEntity(updated));

        } catch (Exception e) {
            log.error("Failed to upload avatar: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload avatar / Ошибка загрузки аватара");
        }
    }
}