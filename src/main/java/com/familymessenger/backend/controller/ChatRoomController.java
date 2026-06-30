package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.*;
import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST контроллер для управления чатами
 * REST controller for chat management
 */
@Slf4j
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;

    /**
     * Получить все чаты текущего пользователя
     * Get all chats of the current user
     *
     * @param user текущий пользователь (из JWT) / current user
     * @return список DTO чатов / list of chat DTOs
     */
    @GetMapping
    public ResponseEntity<List<ChatRoomDto>> getUserChats(@AuthenticationPrincipal User user) {
        log.info("Fetching chats for user: {}", user.getUsername());
        List<ChatRoom> chats = chatService.getChatsForUser(user.getId());
        List<ChatRoomDto> dtos = chats.stream()
                .map(ChatRoomDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Создать новый чат (групповой или семейный)
     * Create a new group/family chat
     *
     * @param request название и тип чата / chat name and type
     * @param user    создатель / creator
     * @return созданный чат / created chat
     */
    @PostMapping
    public ResponseEntity<ChatRoomDto> createChat(@Valid @RequestBody CreateChatRequest request,
                                                  @AuthenticationPrincipal User user) {
        log.info("Creating new chat '{}' by user: {}", request.getName(), user.getUsername());
        ChatRoom chat = chatService.createChat(request.getName(), request.getType(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatRoomDto.fromEntity(chat));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(@PathVariable String chatId,
                                                            @RequestParam(defaultValue = "50") int size,
                                                            @AuthenticationPrincipal User user) {
        log.info("Fetching messages for chat: {} by user: {}", chatId, user.getUsername());
        List<Message> messages = chatService.getMessageHistory(chatId, size);
        List<ChatMessageDto> dtos = messages.stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Удалить чат (только создатель)
     * Delete chat (creator only)
     *
     * @param chatId ID чата / chat ID
     * @param user   текущий пользователь / current user
     */
    @DeleteMapping("/{chatId}")
    public ResponseEntity<?> deleteChat(@PathVariable String chatId,
                                        @AuthenticationPrincipal User user) {
        log.info("Deleting chat {} by user: {}", chatId, user.getUsername());

        chatService.deleteChat(chatId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Получить участников чата
     * Get chat participants
     *
     * @param chatId - ID чата / chat ID
     * @param user - текущий пользователь / current user
     * @return список участников / list of participants
     */
    @GetMapping("/{chatId}/participants")
    public ResponseEntity<List<UserDto>> getParticipants(@PathVariable String chatId,
                                                         @AuthenticationPrincipal User user) {
        log.info("Fetching participants for chat: {} by user: {}", chatId, user.getUsername());

        List<User> participants = chatService.getParticipants(chatId);
        List<UserDto> dtos = participants.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Добавить участников в чат
     * Add participants to chat
     *
     * @param chatId - ID чата / chat ID
     * @param request - список ID пользователей / list of user IDs
     * @param user - текущий пользователь / current user
     * @return обновлённый список участников / updated list of participants
     */
    @PostMapping("/{chatId}/participants")
    public ResponseEntity<List<UserDto>> addParticipants(@PathVariable String chatId,
                                                         @RequestBody List<Long> userIds,
                                                         @AuthenticationPrincipal User user) {
        log.info("Adding participants to chat: {} by user: {}", chatId, user.getUsername());

        List<User> updatedParticipants = chatService.addParticipants(chatId, userIds, user.getId());
        List<UserDto> dtos = updatedParticipants.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Удалить участника из чата
     * Remove participant from chat
     *
     * @param chatId - ID чата / chat ID
     * @param userId - ID пользователя для удаления / user ID to remove
     * @param user - текущий пользователь / current user
     */
    @DeleteMapping("/{chatId}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(@PathVariable String chatId,
                                               @PathVariable Long userId,
                                               @AuthenticationPrincipal User user) {
        log.info("Removing participant {} from chat: {} by user: {}", userId, chatId, user.getUsername());

        chatService.removeParticipant(chatId, userId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Создать групповой чат с участниками
     * Create group chat with participants
     *
     * @param request - запрос с названием и участниками / request with name and participants
     * @param user - текущий пользователь / current user
     * @return созданный чат / created chat
     */
    @PostMapping("/group")
    public ResponseEntity<ChatRoomDto> createGroupChat(@Valid @RequestBody CreateGroupChatRequest request,
                                                       @AuthenticationPrincipal User user) {
        log.info("Creating group chat '{}' by user: {} with {} participants",
                request.getName(), user.getUsername(), request.getParticipantIds().size());

        ChatRoom chat = chatService.createGroupChat(
                request.getName(),
                user.getId(),
                request.getParticipantIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ChatRoomDto.fromEntity(chat));
    }
}