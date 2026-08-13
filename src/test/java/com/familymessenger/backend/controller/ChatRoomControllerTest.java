package com.familymessenger.backend.controller;

import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.security.JwtTokenProvider;
import com.familymessenger.backend.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Проверяет, что ChatRoomController действительно превращает исключения
 * ChatService (авторизация ролей и т.д.) в HTTP 403, а не в 500 или ошибку
 * без обработки, и что каждый эндпоинт вызывает ожидаемый метод сервиса с
 * правильными аргументами. Аутентификация здесь подменяется через
 * with(user(...)) - настоящая проверка правил SecurityConfig (какие пути
 * публичные/требуют JWT) - отдельно, в SecurityFilterChainTest.
 * Verifies that ChatRoomController actually turns ChatService's exceptions
 * (role authorization, etc.) into HTTP 403 rather than a 500 or an unhandled
 * error, and that each endpoint calls the expected service method with the
 * right arguments. Authentication is stubbed via with(user(...)) here - the
 * actual SecurityConfig rules (which paths are public/require a JWT) are
 * covered separately in SecurityFilterChainTest.
 */
@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    // Не используются напрямую в тестах (аутентификация подменяется через
    // with(user(...))), но JwtAuthenticationFilter - это @Component/Filter,
    // которого @WebMvcTest подхватывает автоматически, и его бин всё равно
    // должен собраться - иначе контекст не поднимется.
    // Not used directly in the tests (authentication is stubbed via
    // with(user(...))), but JwtAuthenticationFilter is a @Component/Filter
    // that @WebMvcTest picks up automatically, and its bean still needs to
    // be constructible - otherwise the context fails to start.
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User alice;

    private static final String CHAT_ID = "chat-1";

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
    }

    // --- deleteChat -----------------------------------------------------

    @Test
    void deleteChat_returns204WhenTheServiceAllowsIt() throws Exception {
        mockMvc.perform(delete("/api/chats/{chatId}", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).deleteChat(CHAT_ID, 1L);
    }

    @Test
    void deleteChat_returns403WithTheServiceMessageWhenNotAllowed() throws Exception {
        doThrow(new RuntimeException("Only a superadmin can permanently delete a notebook"))
                .when(chatService).deleteChat(CHAT_ID, 1L);

        mockMvc.perform(delete("/api/chats/{chatId}", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Only a superadmin can permanently delete a notebook"));
    }

    // --- leaveChat -----------------------------------------------------

    @Test
    void leaveChat_returns204OnSuccess() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/leave", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void leaveChat_returns403WhenNotAParticipant() throws Exception {
        doThrow(new RuntimeException("Not a participant of this chat"))
                .when(chatService).leaveChat(CHAT_ID, 1L);

        mockMvc.perform(post("/api/chats/{chatId}/leave", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- roles: admins/editors -----------------------------------------

    @Test
    void promoteGroupAdmin_returns204OnSuccess() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/admins/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).promoteGroupAdmin(CHAT_ID, 2L, 1L);
    }

    @Test
    void promoteGroupAdmin_returns403WhenCallerIsNotCreatorOrSuperadmin() throws Exception {
        doThrow(new RuntimeException("Only the creator or a superadmin can manage group admins"))
                .when(chatService).promoteGroupAdmin(CHAT_ID, 2L, 1L);

        mockMvc.perform(post("/api/chats/{chatId}/admins/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Only the creator or a superadmin can manage group admins"));
    }

    @Test
    void demoteGroupAdmin_returns204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/chats/{chatId}/admins/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).demoteGroupAdmin(CHAT_ID, 2L, 1L);
    }

    @Test
    void promoteEditor_returns204OnSuccess() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/editors/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).promoteEditor(CHAT_ID, 2L, 1L);
    }

    @Test
    void promoteEditor_returns403WhenCallerHasNoRole() throws Exception {
        doThrow(new RuntimeException("Only the creator, a group admin, or a superadmin can manage editors"))
                .when(chatService).promoteEditor(CHAT_ID, 2L, 1L);

        mockMvc.perform(post("/api/chats/{chatId}/editors/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoteEditor_returns204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/chats/{chatId}/editors/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());

        verify(chatService).demoteEditor(CHAT_ID, 2L, 1L);
    }

    // --- removeParticipant -----------------------------------------------

    @Test
    void removeParticipant_returns204OnSuccess() throws Exception {
        mockMvc.perform(delete("/api/chats/{chatId}/participants/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeParticipant_returns403WhenCallerHasNoRightToRemoveThem() throws Exception {
        doThrow(new RuntimeException("Only the creator, a group admin/editor, a superadmin, or the participant themself can remove them"))
                .when(chatService).removeParticipant(CHAT_ID, 2L, 1L);

        mockMvc.perform(delete("/api/chats/{chatId}/participants/{userId}", CHAT_ID, 2L).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- renameChat -----------------------------------------------------

    @Test
    void renameChat_returns200WithTheUpdatedChatOnSuccess() throws Exception {
        ChatRoom renamed = new ChatRoom();
        renamed.setId(CHAT_ID);
        renamed.setName("New Name");
        renamed.setType(ChatRoom.RoomType.GROUP);
        when(chatService.renameChat(eq(CHAT_ID), eq("New Name"), eq(1L))).thenReturn(renamed);

        mockMvc.perform(patch("/api/chats/{chatId}/name", CHAT_ID)
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void renameChat_returns403WhenTheCallerIsNotAParticipant() throws Exception {
        when(chatService.renameChat(eq(CHAT_ID), anyString(), eq(1L)))
                .thenThrow(new RuntimeException("Not a participant of this chat"));

        mockMvc.perform(patch("/api/chats/{chatId}/name", CHAT_ID)
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"New Name\"}"))
                .andExpect(status().isForbidden());
    }

    // --- participant-membership pre-checks (not exceptions, manual gates) --

    @Test
    void getMessages_returns403WhenTheCallerIsNotAParticipant() throws Exception {
        when(chatService.isParticipant(CHAT_ID, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/chats/{chatId}/messages", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());

        verify(chatService, never()).getMessageHistory(anyString(), anyInt(), anyInt());
    }

    @Test
    void getMessages_returns200WhenTheCallerIsAParticipant() throws Exception {
        when(chatService.isParticipant(CHAT_ID, 1L)).thenReturn(true);
        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setId(CHAT_ID);
        when(chatService.getChatRoomById(CHAT_ID)).thenReturn(chatRoom);
        when(chatService.getMessageHistory(eq(CHAT_ID), eq(0), eq(50))).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/chats/{chatId}/messages", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void searchMessages_returns403WhenTheCallerIsNotAParticipant() throws Exception {
        when(chatService.isParticipant(CHAT_ID, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/chats/{chatId}/messages/search", CHAT_ID)
                        .param("query", "hello")
                        .with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getParticipants_returns403WhenTheCallerIsNotAParticipant() throws Exception {
        when(chatService.isParticipant(CHAT_ID, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/chats/{chatId}/participants", CHAT_ID).with(user(alice)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- toggleReaction -----------------------------------------------------

    @Test
    void toggleReaction_returns400WhenEmojiIsMissing() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/messages/{messageId}/reactions", CHAT_ID, "msg-1")
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void toggleReaction_returns403WhenTheCallerIsNotAParticipantOfTheChat() throws Exception {
        when(chatService.toggleReaction(eq("msg-1"), eq(1L), eq("👍")))
                .thenThrow(new RuntimeException("Not a participant of this chat"));

        mockMvc.perform(post("/api/chats/{chatId}/messages/{messageId}/reactions", CHAT_ID, "msg-1")
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isForbidden());
    }

    // --- createChat / createGroupChat: request validation -------------------

    @Test
    void createChat_returns400WhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/chats")
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"type\":\"GROUP\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    void createChat_returns201OnSuccess() throws Exception {
        ChatRoom created = new ChatRoom();
        created.setId(CHAT_ID);
        created.setName("Notes");
        created.setType(ChatRoom.RoomType.GROUP);
        when(chatService.createChat(eq("Notes"), eq(ChatRoom.RoomType.GROUP), eq(1L))).thenReturn(created);

        mockMvc.perform(post("/api/chats")
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Notes\",\"type\":\"GROUP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Notes"));
    }

    @Test
    void createGroupChat_returns400WhenParticipantIdsAreMissing() throws Exception {
        mockMvc.perform(post("/api/chats/group")
                        .with(user(alice)).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Family\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }
}
