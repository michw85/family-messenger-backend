package com.familymessenger.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для ответа аутентификации (JWT токен + данные пользователя)
 * DTO for authentication response (JWT token + user data)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;        // JWT (access) токен для авторизации / JWT access token for authorization
    private String refreshToken; // Токен для обновления access-токена / Token used to obtain a new access token
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

    public AuthResponse(String token, String refreshToken, UserDto user) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.user = user;
    }
}