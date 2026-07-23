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
    private String replyToId;       // ID сообщения, на которое отвечают (входящее поле от клиента) /
                                     // ID of the message being replied to (incoming field from the client)
    private ReplyPreviewDto replyTo; // Превью сообщения, на которое отвечают (исходящее поле для клиента) /
                                     // Preview of the message being replied to (outgoing field for the client)
    private boolean edited;         // Было ли отредактировано / Whether it was edited
    private boolean deleted;        // Было ли удалено (плейсхолдер вместо текста) / Whether it was deleted (placeholder instead of text)
    @Builder.Default
    private boolean read = false;   // Прочитано ли всеми остальными участниками (вычисляется отдельно, не при создании) /
                                     // Whether it's been read by every other participant (computed separately, not at creation)
    @Builder.Default
    private java.util.List<ReactionSummaryDto> reactions = java.util.List.of(); // Реакции на сообщение (вычисляются отдельно) /
                                     // Reactions on the message (computed separately)
    private LocalDateTime revealAt; // Если задано - это капсула времени, откроется в этот момент /
                                     // If set, this is a time capsule that opens at this moment

    /**
     * Правда ли, что содержимое ещё скрыто (капсула времени, момент раскрытия не наступил)
     * Whether the content is still hidden (time capsule, reveal moment hasn't arrived yet)
     */
    private static boolean isHidden(Message message) {
        return message.getRevealAt() != null && message.getRevealAt().isAfter(LocalDateTime.now());
    }

    /**
     * Конвертация из Entity в DTO
     * Convert Entity to DTO
     */
    public static ChatMessageDto fromEntity(Message message) {
        if (message == null) return null;

        boolean hidden = isHidden(message);

        return ChatMessageDto.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom() != null ? message.getChatRoom().getId() : null)
                .sender(message.getSender() != null ? UserDto.fromEntity(message.getSender()) : null)
                .content(hidden ? "🎁 Капсула времени - откроется " + message.getRevealAt() : message.getContent())
                .type(message.getType())
                .mediaUrl(hidden ? null : message.getMediaUrl())
                .timestamp(message.getTimestamp())
                .replyToId(message.getReplyTo() != null ? message.getReplyTo().getId() : null)
                .replyTo(ReplyPreviewDto.fromEntity(message.getReplyTo()))
                .edited(message.isEdited())
                .deleted(message.isDeleted())
                .revealAt(message.getRevealAt())
                .build();
    }
}