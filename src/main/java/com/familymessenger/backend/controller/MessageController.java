package com.familymessenger.backend.controller;

import com.familymessenger.backend.dto.ChatMessageDto;
import com.familymessenger.backend.entity.ChatRoom;
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
                chatMessageDto.getMediaUrl(),
                chatMessageDto.getReplyToId(),
                chatMessageDto.getRevealAt()
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
            ChatRoom chatRoom = savedMessage.getChatRoom();
            // В групповых чатах одно только имя отправителя не говорит, из
            // какого чата пришло сообщение (у человека может быть несколько
            // общих групп) - добавляем название группы в заголовок. В личных
            // чатах имя отправителя уже достаточно однозначно.
            // In group chats the sender's name alone doesn't say which chat
            // the message is from (two people can share several groups) - add
            // the group name to the title. In personal chats the sender's
            // name is already unambiguous.
            String pushTitle = chatRoom.getType() == ChatRoom.RoomType.GROUP && chatRoom.getName() != null
                    ? sender.getUsername() + " • " + chatRoom.getName()
                    : "Новое сообщение от " + sender.getUsername();
            // Капсула времени должна оставаться запечатанной до revealAt -
            // пуш-уведомление не должно раскрывать текст раньше срока (см.
            // ChatMessageDto.fromEntity, где то же самое скрывается при
            // обычной загрузке истории).
            // A time capsule must stay sealed until revealAt - the push
            // notification must not reveal the text early (see
            // ChatMessageDto.fromEntity, which hides the same thing when
            // loading message history normally).
            String pushBody = savedMessage.getRevealAt() != null
                    ? "🎁 Time capsule / Капсула времени"
                    : content;
            Map<String, String> pushData = Map.of(
                    "roomId", roomId,
                    "roomName", chatRoom.getName() != null ? chatRoom.getName() : ""
            );
            for (String token : tokens) {
                fcmService.sendPushNotification(token, pushTitle, pushBody, pushData);
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

        User sender = chatService.getUserByUsername(principal.getName());
        if (!chatService.isParticipant(roomId, sender.getId())) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/typing",
                Map.of("user", principal.getName(), "typing", true)
        );
    }
}