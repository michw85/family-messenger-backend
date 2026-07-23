package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.AuthRequest;
import com.familymessenger.backend.dto.AuthResponse;
import com.familymessenger.backend.dto.LoginRequest;
import com.familymessenger.backend.dto.ResetPasswordRequest;
import com.familymessenger.backend.dto.UserDto;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.UserRepository;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.security.LoginAttemptService;
import com.familymessenger.backend.security.OtpService;
import com.familymessenger.backend.security.PasswordResetService;
import com.familymessenger.backend.security.RateLimiterService;
import com.familymessenger.backend.security.RefreshTokenService;
import com.familymessenger.backend.service.EmailService;
import com.familymessenger.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.Duration;
import java.util.Map;

/**
 * Контроллер для аутентификации и регистрации
 * Controller for authentication and registration
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;

    /**
     * IP клиента с учётом обратного прокси (nginx перед бэкендом на droplet'е)
     * Client IP, accounting for the reverse proxy (nginx in front of the backend on the droplet)
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Регистрация нового пользователя
     * Register new user
     *
     * @param request - данные для регистрации (username, email, password) / registration data
     * @return токен и данные пользователя / token and user data
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {

        log.info("Registering new user with username: {}", request.getUsername());

        // Ограничение по IP - без него эндпоинт можно скриптово забить фейковыми аккаунтами
        // Rate-limit by IP - without this the endpoint could be scripted to mass-create fake accounts
        String ip = clientIp(httpRequest);
        if (!rateLimiterService.tryAcquire("register:ip:" + ip, 5, Duration.ofHours(1))) {
            log.warn("Rate limit exceeded for registration from IP: {}", ip);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many registration attempts. Try again later / Слишком много попыток регистрации. Попробуйте позже");
        }

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

        // Генерируем JWT токен и refresh-токен
        // Generate JWT access token and refresh token
        String jwt = tokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService.issueRefreshToken(user);

        // Конвертируем User в UserDto (чтобы не отправлять пароль)
        // Convert User to UserDto (to avoid sending password)
        UserDto userDto = UserDto.fromEntity(user);

        log.info("User registered successfully: {}", user.getUsername());

        // Возвращаем токен и данные пользователя
        // Return token and user data
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(jwt, refreshToken, userDto));
    }

    /**
     * Шаг 1 логина: проверка пароля и отправка одноразового кода на email (2FA)
     * Login step 1: verify password and send a one-time code to email (2FA)
     *
     * @param request - данные для входа (username, password) / login data
     * @return признак того, что нужно ввести код из письма / indicates a code from email is required
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for username: {}", request.getUsername());

        if (loginAttemptService.isBlocked(request.getUsername())) {
            log.warn("Blocked login attempt (too many failures) for username: {}", request.getUsername());
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many failed login attempts. Try again later / Слишком много неудачных попыток входа. Попробуйте позже");
        }

        User user;
        try {
            // Аутентифицируем пользователя (проверяем username и password)
            // Authenticate user (check username and password)
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            user = userService.findByUsername(request.getUsername());
        } catch (Exception e) {
            // Неверный пароль/username - это единственный случай, который считаем неудачной попыткой
            // Wrong password/username - the only case counted as a failed attempt
            loginAttemptService.recordFailedAttempt(request.getUsername());
            log.error("Login failed for user: {}", request.getUsername(), e);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid username or password / Неверное имя пользователя или пароль");
        }

        // Пароль верный - генерируем и отправляем одноразовый код на email.
        // Ошибку отправки письма НЕ считаем неудачной попыткой входа (не вина пользователя),
        // иначе временный сбой почты мог бы заблокировать пользователя с верным паролем.
        // Password is correct - generate and send a one-time code to email.
        // An email-sending failure is NOT counted as a failed login attempt (not the user's
        // fault), otherwise a temporary mail outage could lock out a user with the right password.
        try {
            String code = otpService.generateAndStore(user.getUsername());
            emailService.sendOtpEmail(user.getEmail(), code);
        } catch (Exception e) {
            log.error("Failed to send OTP email for user: {}", request.getUsername(), e);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Could not send verification code, try again later / Не удалось отправить код подтверждения, попробуйте позже");
        }

        log.info("OTP sent for username: {}", request.getUsername());

        return ResponseEntity.ok(Map.of(
                "otpRequired", true,
                "message", "Verification code sent to email / Код подтверждения отправлен на email"
        ));
    }

    /**
     * Шаг 2 логина: проверка кода из email и выдача токенов
     * Login step 2: verify the email code and issue tokens
     *
     * @param request - тело запроса с полями username и code / request body with username and code fields
     * @return токен и данные пользователя / token and user data
     */
    @PostMapping("/login/verify-otp")
    public ResponseEntity<?> verifyLoginOtp(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String code = request.get("code");

        if (username == null || code == null) {
            return ResponseEntity.badRequest().body("username and code are required / username и code обязательны");
        }

        if (loginAttemptService.isBlocked(username)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many failed attempts. Try again later / Слишком много неудачных попыток. Попробуйте позже");
        }

        if (!otpService.verify(username, code)) {
            loginAttemptService.recordFailedAttempt(username);
            log.warn("Invalid OTP for username: {}", username);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or expired code / Неверный или истёкший код");
        }

        loginAttemptService.resetAttempts(username);

        User user = userService.findByUsername(username);

        String jwt = tokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService.issueRefreshToken(user);
        UserDto userDto = UserDto.fromEntity(user);

        log.info("User logged in successfully (OTP verified): {}", username);

        return ResponseEntity.ok(new AuthResponse(jwt, refreshToken, userDto));
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


    /**
     * Обновление access-токена по refresh-токену (без пароля)
     * Refresh the access token using a refresh token (no password needed)
     *
     * @param request - тело запроса с полем refreshToken / request body with refreshToken field
     * @return новый access-токен и новый refresh-токен (ротация) / new access token and new refresh token (rotation)
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        String oldRefreshToken = request.get("refreshToken");
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            return ResponseEntity.badRequest().body("refreshToken is required / refreshToken обязателен");
        }

        try {
            User user = refreshTokenService.validateAndConsume(oldRefreshToken);

            String newAccessToken = tokenProvider.generateToken(user.getUsername());
            String newRefreshToken = refreshTokenService.issueRefreshToken(user);

            return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshToken, UserDto.fromEntity(user)));
        } catch (Exception e) {
            log.warn("Refresh token rejected: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid or expired refresh token / Недействительный или истёкший refresh-токен");
        }
    }

    /**
     * Выход из аккаунта — отзыв refresh-токена
     * Logout — revoke the refresh token
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> request) {
        String refreshToken = request != null ? request.get("refreshToken") : null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<?> updateFcmToken(@RequestBody Map<String, String> request,
                                            @AuthenticationPrincipal User user) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token is empty");
        }
        user.setFcmToken(token);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /**
     * Шаг 1 сброса пароля: отправка одноразового кода на email (если пользователь с таким email существует).
     * Всегда возвращает один и тот же ответ независимо от того, найден email или нет -
     * чтобы нельзя было проверять, зарегистрирован ли конкретный email (user enumeration).
     * Password reset step 1: send a one-time code to the email (if a user with that email exists).
     * Always returns the same response regardless of whether the email was found - so the
     * endpoint can't be used to check whether a given email is registered (user enumeration).
     *
     * @param request - тело запроса с полем email / request body with an email field
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("email is required / email обязателен");
        }

        // Ограничение и по email (не заспамить письмами один и тот же ящик), и по IP
        // (не заспамить много разных ящиков с одного источника). При превышении лимита
        // отвечаем тем же самым generic-сообщением, что и в обычном случае - иначе сам факт
        // срабатывания лимита мог бы стать ещё одним каналом для user enumeration.
        // Rate-limited both by email (don't spam the same inbox) and by IP (don't spam many
        // different inboxes from one source). On limit exceeded we return the exact same
        // generic message as the normal case - otherwise hitting the limit itself could become
        // another user-enumeration side channel.
        String ip = clientIp(httpRequest);
        boolean ipOk = rateLimiterService.tryAcquire("forgot-password:ip:" + ip, 10, Duration.ofHours(1));
        boolean emailOk = rateLimiterService.tryAcquire("forgot-password:email:" + email.toLowerCase(), 3, Duration.ofHours(1));

        if (ipOk && emailOk) {
            sendPasswordResetIfRegistered(email);
        } else {
            log.warn("Rate limit exceeded for forgot-password (ip={}, email={})", ip, email);
        }

        return ResponseEntity.ok(Map.of(
                "message", "If this email is registered, a reset code has been sent / Если этот email зарегистрирован, на него отправлен код"
        ));
    }

    private void sendPasswordResetIfRegistered(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            try {
                String code = passwordResetService.generateAndStore(email);
                emailService.sendPasswordResetEmail(email, code);
                log.info("Password reset code sent for email: {}", email);
            } catch (Exception e) {
                log.error("Failed to send password reset email for: {}", email, e);
            }
        });
    }

    /**
     * Шаг 2 сброса пароля: проверка кода из email и установка нового пароля.
     * Также отзывает все активные refresh-токены пользователя, чтобы старые
     * сессии (в том числе на устройстве, где пароль забыли не по своей воле)
     * перестали работать.
     * Password reset step 2: verify the emailed code and set a new password.
     * Also revokes all of the user's active refresh tokens, so old sessions
     * (including on a device where the password was forgotten involuntarily)
     * stop working.
     *
     * @param request - email, код из письма и новый пароль / email, the emailed code, and the new password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        User user = userService.findByEmail(request.getEmail()).orElse(null);

        // Намеренно проверяем код даже для несуществующего email, чтобы ответ не отличался
        // по времени/логике от случая "email существует, но код неверный" - защита от enumeration
        // Intentionally verify the code even for a non-existent email, so the response doesn't
        // differ in timing/logic from the "email exists but code is wrong" case - enumeration guard
        boolean codeValid = passwordResetService.verify(request.getEmail(), request.getCode());

        if (user == null || !codeValid) {
            log.warn("Invalid or expired password reset code for email: {}", request.getEmail());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid or expired code / Неверный или истёкший код");
        }

        userService.updatePassword(user, request.getNewPassword());
        refreshTokenService.revokeAllForUser(user);
        loginAttemptService.resetAttempts(user.getUsername());

        log.info("Password reset successfully for user: {}", user.getUsername());

        return ResponseEntity.ok(Map.of(
                "message", "Password reset successfully / Пароль успешно изменён"
        ));
    }
}