package com.familymessenger.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Фильтр для проверки JWT токена в каждом запросе
 * Filter for checking JWT token in every request
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Пропускаем WebSocket запросы без проверки
        String path = request.getRequestURI();
        log.info("Request path: {}", path);
        if (path.startsWith("/api/auth") || path.startsWith("/ws") || path.contains("sockjs")) {
            log.info("Skipping JWT filter for public path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Извлекаем JWT токен из заголовка Authorization
            // Extract JWT token from Authorization header
            String jwt = getJwtFromRequest(request);

            // Если токен есть и он валидный
            // If token exists and is valid
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                // Получаем username из токена
                // Get username from token
                String username = tokenProvider.getUsernameFromToken(jwt);

                // Загружаем данные пользователя из БД
                // Load user details from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Создаем объект аутентификации
                // Create authentication object
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Устанавливаем аутентификацию в контекст Spring Security
                // Set authentication in Spring Security context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("User authenticated: {}", username);
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
        }

        // Продолжаем цепочку фильтров
        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Извлечение JWT токена из заголовка Authorization
     * Extract JWT token from Authorization header
     *
     * @param request - HTTP запрос / HTTP request
     * @return токен или null / token or null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // Заголовок должен выглядеть так: "Bearer eyJhbGciOiJ..."
        // Header should look like: "Bearer eyJhbGciOiJ..."
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // Удаляем "Bearer " / Remove "Bearer "
        }

        return null;
    }
}