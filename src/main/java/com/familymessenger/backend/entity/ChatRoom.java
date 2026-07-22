package com.familymessenger.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private RoomType type = RoomType.GROUP;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToMany
    @JoinTable(
            name = "chat_participants",
            joinColumns = @JoinColumn(name = "chat_room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> participants = new ArrayList<>();

    /**
     * ID пользователей, которые "удалили" этот чат у себя (личные чаты) -
     * чат при этом продолжает существовать для остальных участников.
     * Автоматически очищается, когда в чат приходит новое сообщение.
     * IDs of users who have "deleted" this chat for themselves (personal
     * chats) - the chat still exists for the other participant(s).
     * Automatically cleared when a new message arrives in the chat.
     */
    @ElementCollection
    @CollectionTable(name = "chat_hidden_for", joinColumns = @JoinColumn(name = "chat_room_id"))
    @Column(name = "user_id")
    private Set<Long> hiddenForUserIds = new HashSet<>();

    /**
     * ID пользователей, которые заглушили уведомления по этому чату
     * IDs of users who have muted push notifications for this chat
     */
    @ElementCollection
    @CollectionTable(name = "chat_muted_for", joinColumns = @JoinColumn(name = "chat_room_id"))
    @Column(name = "user_id")
    private Set<Long> mutedForUserIds = new HashSet<>();

    /**
     * Время, до которого каждый участник прочитал сообщения в этом чате
     * (используется для галочек "прочитано" - сообщение считается прочитанным
     * всеми, если время последнего сообщения не позже lastReadAt каждого
     * из остальных участников)
     * Timestamp up to which each participant has read messages in this chat
     * (used for read receipts - a message counts as read by everyone once
     * its timestamp is no later than every other participant's lastReadAt)
     */
    @ElementCollection
    @CollectionTable(name = "chat_read_status", joinColumns = @JoinColumn(name = "chat_room_id"))
    @MapKeyColumn(name = "user_id")
    @Column(name = "last_read_at")
    private Map<Long, LocalDateTime> lastReadAt = new HashMap<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RoomType {
        DIRECT, GROUP, FAMILY
    }
}