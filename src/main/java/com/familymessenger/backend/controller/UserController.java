package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.UserDto;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Поиск пользователей по имени или email (не включая текущего)
     * Search users by username or email (excluding current user)
     *
     * @param query - поисковый запрос / search query
     * @param currentUser - текущий пользователь / current user
     * @return список найденных пользователей / list of found users
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String query,
                                                     @AuthenticationPrincipal User currentUser) {
        log.info("Searching users with query: {} by user: {}", query, currentUser.getUsername());

        List<User> users = userService.searchUsers(query, currentUser.getId());
        List<UserDto> dtos = users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}