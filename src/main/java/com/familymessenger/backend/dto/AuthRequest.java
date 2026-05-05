package com.familymessenger.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для запросов аутентификации (регистрация и логин)
 * DTO for authentication requests (registration and login)
 */
@Data
public class AuthRequest {

    @NotBlank(message = "Username is required / Имя пользователя обязательно")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters / Имя пользователя должно быть от 3 до 50 символов")
    private String username;

    @Email(message = "Invalid email format / Неверный формат email")
    private String email;

    @NotBlank(message = "Password is required / Пароль обязателен")
    @Size(min = 6, message = "Password must be at least 6 characters / Пароль должен быть минимум 6 символов")
    private String password;
}