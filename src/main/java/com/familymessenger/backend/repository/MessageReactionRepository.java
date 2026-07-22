package com.familymessenger.backend.repository;

import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.MessageReaction;
import com.familymessenger.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, String> {

    List<MessageReaction> findByMessage(Message message);

    Optional<MessageReaction> findByMessageAndUser(Message message, User user);
}
