package com.familymessenger.backend.controller;

import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.security.RateLimiterService;
import com.familymessenger.backend.service.ChatService;
import com.familymessenger.backend.service.FileService;
import com.familymessenger.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Проверяет HTTP-слой UserController: превращение исключений ChatService
 * (блокировка входа - только суперадмин) в 403, лимит частоты поиска
 * пользователей и валидацию загрузки аватара.
 * Verifies UserController's HTTP layer: ChatService exceptions (login
 * blacklisting - superadmin only) turning into 403, the user-search rate
 * limit, and avatar upload validation.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");

        // Большинство тестов не про rate limit - разрешаем по умолчанию, чтобы
        // не дублировать эту стаб-строку в каждом тесте
        // Most tests aren't about the rate limit - allow by default so this
        // stub line doesn't need repeating in every test
        lenient().when(rateLimiterService.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
    }

    // --- searchUsers -----------------------------------------------------

    @Test
    void searchUsers_returns200WithResultsWhenUnderTheRateLimit() throws Exception {
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        when(userService.searchUsers("bo", 1L)).thenReturn(java.util.List.of(bob));

        mockMvc.perform(get("/api/users/search").param("query", "bo").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("bob"));
    }

    @Test
    void searchUsers_returns429WhenTheRateLimitIsExceeded() throws Exception {
        when(rateLimiterService.tryAcquire(eq("user-search:1"), anyInt(), any(Duration.class))).thenReturn(false);

        mockMvc.perform(get("/api/users/search").param("query", "bo").with(user(alice)))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(userService);
    }

    // --- blacklistUser / unblacklistUser ----------------------------------

    @Test
    void blacklistUser_returns204WhenTheCallerIsASuperadmin() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/blacklist", 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).blacklistUser(2L, 1L);
    }

    @Test
    void blacklistUser_returns403WhenTheCallerIsNotASuperadmin() throws Exception {
        doThrow(new RuntimeException("Only a superadmin can blacklist users"))
                .when(chatService).blacklistUser(2L, 1L);

        mockMvc.perform(post("/api/users/{userId}/blacklist", 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Only a superadmin can blacklist users"));
    }

    @Test
    void unblacklistUser_returns204WhenTheCallerIsASuperadmin() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/blacklist", 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).unblacklistUser(2L, 1L);
    }

    @Test
    void unblacklistUser_returns403WhenTheCallerIsNotASuperadmin() throws Exception {
        doThrow(new RuntimeException("Only a superadmin can unblacklist users"))
                .when(chatService).unblacklistUser(2L, 1L);

        mockMvc.perform(delete("/api/users/{userId}/blacklist", 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- uploadAvatar -----------------------------------------------------

    @Test
    void uploadAvatar_returns400ForAnEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/users/me/avatar").file(empty).with(user(alice)).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadAvatar_returns400ForANonImageFile() throws Exception {
        MockMultipartFile notAnImage = new MockMultipartFile("file", "doc.pdf", "application/pdf", "hi".getBytes());

        mockMvc.perform(multipart("/api/users/me/avatar").file(notAnImage).with(user(alice)).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadAvatar_returns400ForAFileLargerThan10Mb() throws Exception {
        MockMultipartFile tooLarge = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

        mockMvc.perform(multipart("/api/users/me/avatar").file(tooLarge).with(user(alice)).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadAvatar_returns200OnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", "hi".getBytes());
        when(fileService.uploadFile(any(), eq("avatars"), eq(alice))).thenReturn("https://bonds-app.duckdns.org/media/avatars/1_x.jpg");
        User updated = new User();
        updated.setId(1L);
        updated.setUsername("alice");
        updated.setAvatarUrl("https://bonds-app.duckdns.org/media/avatars/1_x.jpg");
        when(userService.updateAvatar(eq(1L), anyString())).thenReturn(updated);

        mockMvc.perform(multipart("/api/users/me/avatar").file(file).with(user(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://bonds-app.duckdns.org/media/avatars/1_x.jpg"));
    }
}
