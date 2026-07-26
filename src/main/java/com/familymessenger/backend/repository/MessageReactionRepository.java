package com.familymessenger.backend.repository;

import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.MessageReaction;
import com.familymessenger.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, String> {

    List<MessageReaction> findByMessage(Message message);

    Optional<MessageReaction> findByMessageAndUser(Message message, User user);

    /**
     * Удаляет реакции на все сообщения чата - нужно перед удалением самих
     * сообщений (иначе внешний ключ message_id не даст их стереть)
     * Deletes reactions on all of a chat's messages - needed before deleting
     * the messages themselves (otherwise the message_id foreign key blocks it)
     */
    @Modifying
    @Query("DELETE FROM MessageReaction r WHERE r.message.chatRoom = :chatRoom")
    void deleteByMessageChatRoom(@Param("chatRoom") ChatRoom chatRoom);
}
