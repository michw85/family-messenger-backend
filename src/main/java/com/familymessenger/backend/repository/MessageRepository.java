package com.familymessenger.backend.repository;

import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.ChatRoom;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Optional<Message> findFirstByMediaUrlEndingWith(String suffix);

    Optional<Message> findFirstByChatRoomOrderByTimestampDesc(ChatRoom chatRoom);

    List<Message> findByChatRoomOrderByTimestampAsc(ChatRoom chatRoom);

    List<Message> findByChatRoomAndContentContainingIgnoreCaseOrderByTimestampDesc(ChatRoom chatRoom, String content);

    List<Message> findByChatRoomOrderByTimestampDesc(ChatRoom chatRoom, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :chatRoom AND m.timestamp > :since ORDER BY m.timestamp ASC")
    List<Message> findRecentMessages(@Param("chatRoom") ChatRoom chatRoom, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatRoom = :chatRoom AND m.timestamp > :since")
    long countNewMessages(@Param("chatRoom") ChatRoom chatRoom, @Param("since") LocalDateTime since);

    /**
     * Сообщения из этого чата, отправленные в этот же день (число+месяц) в прошлые годы
     * ("лента памяти") - не считая сегодняшний год и удалённые сообщения
     * Messages from this chat sent on this same day (day+month) in past years
     * ("memory lane") - excluding this year and deleted messages
     */
    @Query("SELECT m FROM Message m WHERE m.chatRoom = :chatRoom " +
            "AND EXTRACT(MONTH FROM m.timestamp) = :month " +
            "AND EXTRACT(DAY FROM m.timestamp) = :day " +
            "AND EXTRACT(YEAR FROM m.timestamp) < :currentYear " +
            "AND m.deleted = false " +
            "ORDER BY m.timestamp DESC")
    List<Message> findOnThisDayInPast(@Param("chatRoom") ChatRoom chatRoom,
                                       @Param("month") int month,
                                       @Param("day") int day,
                                       @Param("currentYear") int currentYear);

    /**
     * Сообщения VIDEO/FILE, срок хранения которых истёк, а файл ещё не удалён -
     * см. MediaRetentionService
     * VIDEO/FILE messages whose retention window has passed and the file
     * hasn't been removed yet - see MediaRetentionService
     */
    @Query("SELECT m FROM Message m WHERE m.mediaExpiresAt IS NOT NULL " +
            "AND m.mediaExpiresAt < :cutoff AND m.mediaDeletedFromStorage = false")
    List<Message> findExpiredMedia(@Param("cutoff") LocalDateTime cutoff);
}