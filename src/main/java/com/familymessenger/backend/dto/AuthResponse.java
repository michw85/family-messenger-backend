package com.familymessenger.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO для ответа аутентификации (JWT токен + данные пользователя)
 * DTO for authentication response (JWT token + user data)
 */
@Data
@Builder
public class AuthResponse {

    private String token;       // JWT токен для авторизации / JWT token for authorization
    private String type = "Bearer";  // Тип токена (всегда Bearer) / Token type (always Bearer)
    private UserDto user;       // Данные пользователя / User data

    /**
     * Конструктор для создания ответа с токеном и пользователем
     * Constructor for creating response with token and user
     *
     * @param token - JWT токен / JWT token
     * @param user - данные пользователя / user data
     */
    public AuthResponse(String token, UserDto user) {
        this.token = token;
        this.user = user;
    }
}