package com.familymessenger.backend.controller;

import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.security.SecurityConfig;
import com.familymessenger.backend.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверяет реальную цепочку SecurityConfig (не подмену через with(user(...)),
 * как в остальных *ControllerTest) - до сих пор ни разу не проверенный кусок:
 * действительно ли запрос без токена отклоняется, а с валидным - проходит.
 * Verifies the real SecurityConfig chain (not the with(user(...)) stand-in
 * used in the other *ControllerTest classes) - a piece that had never been
 * exercised before: does a request with no token actually get rejected, and
 * one with a valid token actually get through.
 */
@WebMvcTest(ChatRoomController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:19006")
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void aProtectedEndpointRejectsARequestWithNoAuthorizationHeaderAtAll() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aProtectedEndpointRejectsAnInvalidOrExpiredToken() throws Exception {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(false);

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer garbage-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aProtectedEndpointLetsAValidTokenThrough() throws Exception {
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("valid-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(alice);
        when(chatService.getChatsForUser(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }
}
