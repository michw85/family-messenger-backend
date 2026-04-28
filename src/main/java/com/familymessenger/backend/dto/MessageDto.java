package com.familymessenger.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.familymessenger.backend.entity.Message;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private String id;
    private String chatRoomId;
    private UserDto sender;
    private String content;
    private Message.MessageType type;
    private String mediaUrl;
    private LocalDateTime timestamp;

    // Конвертация из Entity в DTO
    public static MessageDto fromEntity(Message message) {
        return MessageDto.builder()
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