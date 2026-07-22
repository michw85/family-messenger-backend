package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Конфигурация безопасности Spring Security
 * Spring Security configuration
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Настройка цепочки фильтров безопасности
     * Security filter chain configuration
     *
     * - Отключаем CSRF (для REST API используется JWT) / Disable CSRF (JWT for REST API)
     * - Настраиваем CORS для мобильного приложения / Configure CORS for mobile app
     * - Отключаем X-Frame-Options для H2 консоли / Disable X-Frame-Options for H2 console
     * - Делаем сессии stateless (без сессий на сервере) / Stateless sessions (no server sessions)
     * - Открываем доступ для эндпоинтов авторизации, WebSocket и H2 консоли / Open access for auth, WebSocket and H2 endpoints
     * - Все остальные запросы требуют аутентификации через JWT / All other requests require JWT authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())                              // Отключаем CSRF (для REST API) / Disable CSRF for REST API
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable())) // Для H2
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Без сессий (используем JWT) / Stateless (using JWT)
                )
                // Без явного entryPoint Spring по умолчанию отвечает 403 (Http403ForbiddenEntryPoint)
                // на любой запрос без валидного JWT - в том числе на протухший access-токен.
                // Фронтенд же перевыпускает токен только по 401, поэтому протухший токен без
                // этого исправления никогда не обновлялся автоматически и требовал ручного релогина.
                // Without an explicit entry point Spring's default is to answer 403
                // (Http403ForbiddenEntryPoint) for any request without a valid JWT - including an
                // expired access token. The frontend only refreshes the token on 401, so without
                // this fix an expired token never got auto-refreshed and forced a manual re-login.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // Открытые эндпоинты (без авторизации) / Public endpoints (no auth required)
                        // refresh/logout работают по refresh-токену в теле запроса, а не по JWT,
                        // поэтому не требуют действующего access-токена
                        // refresh/logout authenticate via the refresh token in the request body,
                        // not the JWT, so they don't require a valid access token
                        .requestMatchers("/api/auth/login", "/api/auth/login/verify-otp", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/ws/**", "/ws").permitAll()
                        // Swagger UI (если добавим позже) / Swagger UI (if added later)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // База данных - H2 консоль / Database - H2 console
                        .requestMatchers("/h2-console/**").permitAll()
                        // /auth/fcm-token не должен быть публичным эндпоинтом — ему нужна авторизация, чтобы знать, чей токен обновлять / It shouldn't be a public endpoint—it requires authorization to know whose token to refresh
                        .requestMatchers("/api/auth/fcm-token", "/api/auth/me").authenticated()
                        // Все остальные запросы требуют авторизации / All other requests require authentication
                        .anyRequest().authenticated()
                )
                // Добавляем наш JWT фильтр перед стандартным фильтром
                // Add our JWT filter before the standard filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Кодировщик паролей (BCrypt - безопасное хэширование)
     * Password encoder (BCrypt - secure hashing)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Менеджер аутентификации (нужен для логина)
     * Authentication manager (required for login)
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Настройка CORS (Cross-Origin Resource Sharing)
     * CORS (Cross-Origin Resource Sharing) configuration
     *
     * Нативное мобильное приложение не отправляет заголовок Origin, поэтому CORS
     * здесь в первую очередь защищает от браузерных/веб-клиентов с чужих доменов.
     * "*" вместе с allowCredentials(true) недопустим (Spring это отклоняет) и просто
     * небезопасен, поэтому ограничиваем списком доверенных доменов из конфигурации.
     * A native mobile app doesn't send an Origin header, so CORS here mainly guards
     * against browser/web clients from other domains. "*" combined with
     * allowCredentials(true) is rejected by Spring and unsafe anyway, so we restrict
     * to a configured allow-list of trusted domains instead.
     *
     * @return источник CORS конфигурации / CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Список доверенных доменов из application.properties (app.cors.allowed-origins)
        // Allow-list of trusted domains from application.properties (app.cors.allowed-origins)
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        // Разрешаем HTTP методы, которые использует приложение
        // Allow HTTP methods used by the application
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Разрешаем все заголовки
        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Разрешаем отправку учётных данных (cookies, авторизация)
        // Allow sending credentials (cookies, authorization)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Применяем CORS настройки ко всем эндпоинтам
        // Apply CORS settings to all endpoints
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}