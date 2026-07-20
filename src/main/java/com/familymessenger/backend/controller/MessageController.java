package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.ChatMessageDto;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.ChatService;
import com.familymessenger.backend.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WebSocket контроллер для обработки сообщений чата
 * WebSocket controller for chat message handling
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageController {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmService fcmService;

    /**
     * Базовая проверка и очистка текста сообщения: ограничение длины и
     * удаление управляющих символов (защита от переполнения и мусора,
     * который может быть использован для инъекций в будущих веб-клиентах)
     * Basic message content validation and sanitization: length cap and
     * stripping of control characters (guards against overflow and payloads
     * that could be abused for injection in future web-based clients)
     */
    private String sanitizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message content exceeds maximum length");
        }
        // Убираем управляющие символы, кроме перевода строки и табуляции
        // Strip control characters except newline and tab
        return trimmed.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
    }

    /**
     * Отправка сообщения в комнату чата
     * Send message to chat room
     *
     * @param chatMessageDto - сообщение / message
     * @param roomId - ID комнаты / room ID
     * @param principal - текущий авторизованный пользователь / current authenticated user
     * @return сообщение для всех подписчиков / message for all subscribers
     */
    @MessageMapping("/chat.send/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public ChatMessageDto sendMessage(@Payload ChatMessageDto chatMessageDto,
                                      @DestinationVariable String roomId,
                                      Principal principal) {


        log.info("Received message in room {} from user: {}", roomId, principal.getName());

        // Получаем текущего пользователя
        // Get current user
        User sender = chatService.getUserByUsername(principal.getName());

        // Проверяем, что отправитель — участник комнаты (иначе нельзя писать/подслушивать чужой чат)
        // Verify sender is a participant of the room (otherwise they could write into/eavesdrop on someone else's chat)
        if (!chatService.isParticipant(roomId, sender.getId())) {
            log.warn("User {} tried to send a message to room {} without being a participant", sender.getUsername(), roomId);
            throw new IllegalStateException("Not a participant of this chat room");
        }

        String content = sanitizeContent(chatMessageDto.getContent());

        // Сохраняем сообщение в базу данных
        // Save message to database
        Message savedMessage = chatService.saveMessage(
                content,
                roomId,
                sender,
               chatMessageDto.getType() != null ? chatMessageDto.getType() : Message.MessageType.TEXT,
                chatMessageDto.getMediaUrl()
        );

        // Отправить уведомления участникам
        /*try {
            List<User> participants = chatService.getParticipants(roomId);
            for (User recipient : participants) {
                if (!recipient.getId().equals(sender.getId()) && recipient.getFcmToken() != null && !recipient.getFcmToken().isEmpty()) {
                    fcmService.sendPushNotification(
                            recipient.getFcmToken(),
                            "Новое сообщение от " + sender.getUsername(),
                            chatMessageDto.getContent()
                    );
                }
            }*/
        try {
            List<String> tokens = chatService.getParticipantFcmTokens(roomId, sender.getId());
            Map<String, String> pushData = Map.of(
                    "roomId", roomId,
                    "roomName", savedMessage.getChatRoom().getName()
            );
            for (String token : tokens) {
                fcmService.sendPushNotification(
                        token,
                        "Новое сообщение от " + sender.getUsername(),
                        content,
                        pushData
                );
            }
        } catch (Exception e) {
            log.error("Failed to send push notifications", e);
            // Не прерываем выполнение
        }

        // Конвертируем в DTO и возвращаем
        // Convert to DTO and return
        return ChatMessageDto.fromEntity(savedMessage);
    }

    /**
     * Отправка личного сообщения (прямой чат)
     * Send private message (direct chat)
     *
     * @param chatMessageDto - сообщение / message
     * @param principal - текущий пользователь / current user
     */
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessageDto chatMessageDto,
                                   Principal principal) {

        log.info("Sending private message from {} to {}",
                principal.getName(), chatMessageDto.getChatRoomId());

        User sender = chatService.getUserByUsername(principal.getName());

        // Сохраняем личное сообщение
        // Save private message
        Message savedMessage = chatService.savePrivateMessage(
                sanitizeContent(chatMessageDto.getContent()),
                chatMessageDto.getChatRoomId(),
                sender
        );

        ChatMessageDto response = ChatMessageDto.fromEntity(savedMessage);

        // Отправляем сообщение конкретному пользователю
        // Send message to specific user
        messagingTemplate.convertAndSendToUser(
                chatMessageDto.getChatRoomId(),  // recipient's username
                "/queue/private",
                response
        );
    }

    /**
     * Индикатор печатания (пользователь печатает)
     * Typing indicator (user is typing)
     *
     * @param roomId - ID комнаты / room ID
     * @param principal - текущий пользователь / current user
     */
    @MessageMapping("/typing/{roomId}")
    public void typing(@DestinationVariable String roomId, Principal principal) {

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/typing",
                Map.of("user", principal.getName(), "typing", true)
        );
    }
}