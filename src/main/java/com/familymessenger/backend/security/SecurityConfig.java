package com.familymessenger.backend.security;

import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
                .authorizeHttpRequests(auth -> auth
                        // Открытые эндпоинты (без авторизации) / Public endpoints (no auth required)
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/ws/**", "/ws").permitAll()
                        // Swagger UI (если добавим позже) / Swagger UI (if added later)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Файлы - скачивание / Files - download
                        .requestMatchers("/api/files/download/**").permitAll()
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
     * Настройка CORS (Cross-Origin Resource Sharing) для разрешения запросов с мобильного приложения
     * CORS (Cross-Origin Resource Sharing) configuration to allow requests from mobile app
     *
     * Разрешаем запросы с любых источников (для разработки, позже ограничим доменом)
     * Allow requests from any origin (for development, later restrict to specific domain)
     *
     * @return источник CORS конфигурации / CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Разрешаем запросы с любых источников (для разработки с мобильных устройств)
        // Allow requests from any origin (for mobile development)
        configuration.setAllowedOrigins(Arrays.asList("*"));

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