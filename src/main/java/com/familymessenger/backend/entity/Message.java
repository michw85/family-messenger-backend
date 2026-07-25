package com.familymessenger.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;

    /**
     * Сообщение, на которое отвечает это (null, если это не ответ)
     * The message this one is replying to (null if it's not a reply)
     */
    @ManyToOne
    @JoinColumn(name = "reply_to_id")
    private Message replyTo;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.TEXT;

    private String mediaUrl;

    private LocalDateTime timestamp;

    /**
     * Если задано - это "капсула времени": содержимое (и медиа) скрыто от
     * ВСЕХ, включая отправителя, до этого момента - see ChatMessageDto.fromEntity.
     * If set, this is a "time capsule": the content (and media) is hidden
     * from EVERYONE, including the sender, until this moment - see
     * ChatMessageDto.fromEntity.
     */
    private LocalDateTime revealAt;

    /**
     * Для VIDEO/FILE - момент, когда сам файл (не сообщение) должен быть
     * удалён с сервера планировщиком, см. MediaRetentionService. Null для
     * остальных типов - фото/голосовые не удаляются.
     * For VIDEO/FILE - when the file itself (not the message) should be
     * removed from storage by the scheduler, see MediaRetentionService.
     * Null for other types - photos/voice messages are never auto-deleted.
     */
    private LocalDateTime mediaExpiresAt;

    // columnDefinition даёт Hibernate указание на DEFAULT при авто-миграции схемы,
    // иначе ALTER TABLE ADD COLUMN NOT NULL падает на таблице с существующими строками
    // columnDefinition tells Hibernate to add a DEFAULT during schema auto-migration,
    // otherwise ALTER TABLE ADD COLUMN NOT NULL fails on a table with existing rows
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean edited = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    /**
     * true, как только планировщик реально удалил файл из MinIO - отдельно
     * от mediaExpiresAt, чтобы не пытаться повторно удалить уже удалённый
     * объект и не спорить с часовой арифметикой при чтении.
     * true once the scheduler has actually removed the file from MinIO -
     * kept separate from mediaExpiresAt so we don't retry removing an
     * already-gone object or rely purely on clock math when reading.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean mediaDeletedFromStorage = false;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
        if ((type == MessageType.VIDEO || type == MessageType.FILE) && mediaUrl != null) {
            mediaExpiresAt = timestamp.plusDays(30);
        }
    }

    public enum MessageType {
        TEXT, IMAGE, VOICE, VIDEO, FILE, MOOD_CHECKIN,
        CALL_MISSED, CALL_DECLINED, CALL_ANSWERED, CALL_CANCELLED,
        RICH_TEXT, CHECKLIST
    }
}