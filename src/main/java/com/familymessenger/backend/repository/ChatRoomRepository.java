package com.familymessenger.backend.repository;

import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {

    List<ChatRoom> findByParticipantsContaining(User user);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.type = 'DIRECT' AND :user MEMBER OF cr.participants AND :otherUser MEMBER OF cr.participants")
    Optional<ChatRoom> findDirectChatBetweenUsers(@Param("user") User user, @Param("otherUser") User otherUser);

    List<ChatRoom> findByNameContainingIgnoreCase(String name);
}