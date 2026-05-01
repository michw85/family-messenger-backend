package com.familymessenger.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.familymessenger.backend.entity.Message;
import java.time.LocalDateTime;

/**
 * DTO для сообщений чата (WebSocket)
 * DTO for chat messages (WebSocket)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private String id;              // Уникальный ID сообщения / Unique message ID
    private String chatRoomId;      // ID комнаты чата / Chat room ID
    private UserDto sender;         // Отправитель / Sender
    private String content;         // Текст сообщения / Message text
    private Message.MessageType type; // Тип сообщения (TEXT, IMAGE, VOICE) / Message type
    private String mediaUrl;        // Ссылка на файл (фото/голос) / Media file URL
    private LocalDateTime timestamp; // Время отправки / Timestamp

    /**
     * Конвертация из Entity в DTO
     * Convert Entity to DTO
     */
    public static ChatMessageDto fromEntity(Message message) {
        if (message == null) return null;

        return ChatMessageDto.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom() != null ? message.getChatRoom().getId() : null)
                .sender(message.getSender() != null ? UserDto.fromEntity(message.getSender()) : null)
                .content(message.getContent())
                .type(message.getType())
                .mediaUrl(message.getMediaUrl())
                .timestamp(message.getTimestamp())
                .build();
    }
}