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

    // columnDefinition даёт Hibernate указание на DEFAULT при авто-миграции схемы,
    // иначе ALTER TABLE ADD COLUMN NOT NULL падает на таблице с существующими строками
    // columnDefinition tells Hibernate to add a DEFAULT during schema auto-migration,
    // otherwise ALTER TABLE ADD COLUMN NOT NULL fails on a table with existing rows
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean edited = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    public enum MessageType {
        TEXT, IMAGE, VOICE, VIDEO, FILE, MOOD_CHECKIN
    }
}