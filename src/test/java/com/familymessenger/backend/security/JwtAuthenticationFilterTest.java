package com.familymessenger.backend.security;

import com.familymessenger.backend.entity.User;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Проверяет, что фильтр действительно не аутентифицирует запрос с валидным
 * JWT, если пользователь на момент запроса заблокирован или ещё не
 * подтверждён суперадмином - это единственное место, где такая проверка
 * происходит НЕ только при логине (см. isEnabled() в User).
 * Verifies the filter actually refuses to authenticate a request with a
 * valid JWT if the user is, at request time, blacklisted or not yet
 * approved by a superadmin - the only place this check happens outside of
 * login time (see isEnabled() on User).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User enabledUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        return user;
    }

    private User disabledUser(boolean blacklisted, boolean approved) {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setBlacklisted(blacklisted);
        user.setApproved(approved);
        return user;
    }

    private MockHttpServletRequest requestWithToken(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void authenticatesAnEnabledUserWithAValidToken() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(enabledUser());

        filter.doFilter(requestWithToken("/api/chats", "valid-token"), new MockHttpServletResponse(), filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void doesNotAuthenticateABlacklistedUserEvenWithAValidToken() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("bob");
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(disabledUser(true, true));

        filter.doFilter(requestWithToken("/api/chats", "valid-token"), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void doesNotAuthenticateAUserAwaitingApprovalEvenWithAValidToken() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("bob");
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(disabledUser(false, false));

        filter.doFilter(requestWithToken("/api/chats", "valid-token"), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doesNotAuthenticateWithoutAToken() throws Exception {
        filter.doFilter(requestWithToken("/api/chats", null), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void doesNotAuthenticateWithAnInvalidToken() throws Exception {
        when(tokenProvider.validateToken("garbage")).thenReturn(false);

        filter.doFilter(requestWithToken("/api/chats", "garbage"), new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void skipsTheTokenCheckEntirelyForThePublicLoginPath() throws Exception {
        filter.doFilter(requestWithToken("/api/auth/login", null), new MockHttpServletResponse(), filterChain);

        verifyNoInteractions(tokenProvider, userDetailsService);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void doesNotSkipTheAuthMePathEvenThoughItStartsWithApiAuth() throws Exception {
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("valid-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(enabledUser());

        filter.doFilter(requestWithToken("/api/auth/me", "valid-token"), new MockHttpServletResponse(), filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
