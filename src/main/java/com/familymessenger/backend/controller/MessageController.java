package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.ChatMessageDto;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.service.ChatService;
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
import java.util.Map;

/**
 * WebSocket контроллер для обработки сообщений чата
 * WebSocket controller for chat message handling
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

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

        // Сохраняем сообщение в базу данных
        // Save message to database
        Message savedMessage = chatService.saveMessage(
                chatMessageDto.getContent(),
                roomId,
                sender,
               chatMessageDto.getType() != null ? chatMessageDto.getType() : Message.MessageType.TEXT,
                chatMessageDto.getMediaUrl()
        );

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
                chatMessageDto.getContent(),
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