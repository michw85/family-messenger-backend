package com.familymessenger.backend.dto;

import com.familymessenger.backend.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Краткий превью сообщения, на которое отвечают (для отображения цитаты
 * в клиенте без отдельного запроса за оригиналом)
 * A short preview of the message being replied to (so the client can
 * render the quoted snippet without a separate request for the original)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyPreviewDto {

    private String id;
    private String senderUsername;
    private String content;
    private Message.MessageType type;
    private boolean deleted;

    public static ReplyPreviewDto fromEntity(Message message) {
        if (message == null) return null;

        // Капсула времени остаётся скрытой даже в превью цитаты - иначе ответ на неё раскрыл бы её раньше срока
        // A time capsule stays hidden even in the reply preview - otherwise replying to it would reveal it early
        boolean hidden = message.getRevealAt() != null && message.getRevealAt().isAfter(LocalDateTime.now());

        return ReplyPreviewDto.builder()
                .id(message.getId())
                .senderUsername(message.getSender() != null ? message.getSender().getUsername() : null)
                .content(hidden ? "🎁 Капсула времени - откроется " + message.getRevealAt() : message.getContent())
                .type(message.getType())
                .deleted(message.isDeleted())
                .build();
    }
}
