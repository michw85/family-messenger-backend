package com.familymessenger.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для шага 2 сброса пароля (проверка кода из письма + новый пароль)
 * DTO for password reset step 2 (verify the emailed code + new password)
 */
@Data
public class ResetPasswordRequest {

    @Email(message = "Invalid email format / Неверный формат email")
    @NotBlank(message = "Email is required / Email обязателен")
    private String email;

    @NotBlank(message = "Code is required / Код обязателен")
    private String code;

    @NotBlank(message = "Password is required / Пароль обязателен")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters / Пароль должен быть минимум 8 символов")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = "Password must contain an uppercase letter, a lowercase letter and a digit / Пароль должен содержать заглавную и строчную буквы, а также цифру"
    )
    private String newPassword;
}
