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

    List<Message> findByChatRoomOrderByTimestampAsc(ChatRoom chatRoom);

    List<Message> findByChatRoomAndContentContainingIgnoreCaseOrderByTimestampDesc(ChatRoom chatRoom, String content);

    List<Message> findByChatRoomOrderByTimestampDesc(ChatRoom chatRoom, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.chatRoom = :chatRoom AND m.timestamp > :since ORDER BY m.timestamp ASC")
    List<Message> findRecentMessages(@Param("chatRoom") ChatRoom chatRoom, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatRoom = :chatRoom AND m.timestamp > :since")
    long countNewMessages(@Param("chatRoom") ChatRoom chatRoom, @Param("since") LocalDateTime since);
}