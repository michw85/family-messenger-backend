package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.AuthRequest;
import com.familymessenger.backend.dto.AuthResponse;
import com.familymessenger.backend.dto.LoginRequest;
import com.familymessenger.backend.dto.UserDto;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Контроллер для аутентификации и регистрации
 * Controller for authentication and registration
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    /**
     * Регистрация нового пользователя
     * Register new user
     *
     * @param request - данные для регистрации (username, email, password) / registration data
     * @return токен и данные пользователя / token and user data
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {

        log.info("Registering new user with username: {}", request.getUsername());

        // Проверяем, существует ли пользователь с таким username
        // Check if user with this username already exists
        if (userService.existsByUsername(request.getUsername())) {
            log.warn("Username {} is already taken", request.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body("Username is already taken / Имя пользователя уже занято");
        }

        // Проверяем, существует ли пользователь с таким email
        // Check if user with this email already exists
        if (userService.existsByEmail(request.getEmail())) {
            log.warn("Email {} is already registered", request.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body("Email is already registered / Email уже зарегистрирован");
        }

        // Создаём нового пользователя
        // Create new user
        User user = userService.registerNewUser(request);

        // Генерируем JWT токен
        // Generate JWT token
        String jwt = tokenProvider.generateToken(user.getUsername());

        // Конвертируем User в UserDto (чтобы не отправлять пароль)
        // Convert User to UserDto (to avoid sending password)
        UserDto userDto = UserDto.fromEntity(user);

        log.info("User registered successfully: {}", user.getUsername());

        // Возвращаем токен и данные пользователя
        // Return token and user data
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(jwt, userDto));
    }

    /**
     * Логин пользователя
     * User login
     *
     * @param request - данные для входа (username, password) / login data
     * @return токен и данные пользователя / token and user data
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for username: {}", request.getUsername());

        try {
            // Аутентифицируем пользователя (проверяем username и password)
            // Authenticate user (check username and password)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Устанавливаем аутентификацию в контекст Spring Security
            // Set authentication in Spring Security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Генерируем JWT токен
            // Generate JWT token
            String jwt = tokenProvider.generateToken(request.getUsername());

            // Загружаем данные пользователя из БД
            // Load user data from database
            User user = userService.findByUsername(request.getUsername());
            UserDto userDto = UserDto.fromEntity(user);

            log.info("User logged in successfully: {}", request.getUsername());

            // Возвращаем токен и данные пользователя
            // Return token and user data
            return ResponseEntity.ok(new AuthResponse(jwt, userDto));

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password / Неверное имя пользователя или пароль");
        }
    }

    /**
     * Получение информации о текущем пользователе (по токену)
     * Get current user info (from token)
     *
     * @param user - текущий пользователь (автоматически подставляется Spring) / current user
     * @return данные пользователя / user data
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(@AuthenticationPrincipal User user) {

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDto userDto = UserDto.fromEntity(user);
        return ResponseEntity.ok(userDto);
    }
}