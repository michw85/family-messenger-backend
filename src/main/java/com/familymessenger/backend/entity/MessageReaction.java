package com.familymessenger.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Реакция (emoji) пользователя на сообщение. Один пользователь может
 * оставить только одну реакцию на сообщение - повторный выбор той же
 * эмодзи снимает реакцию, выбор другой - заменяет предыдущую.
 * A user's emoji reaction to a message. A user can only have one
 * reaction per message - picking the same emoji again removes it,
 * picking a different one replaces the previous reaction.
 */
@Entity
@Table(name = "message_reactions", uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "message_id")
    private Message message;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String emoji;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
