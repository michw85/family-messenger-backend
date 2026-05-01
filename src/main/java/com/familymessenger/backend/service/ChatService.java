package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.ChatRoomRepository;
import com.familymessenger.backend.repository.MessageRepository;
import com.familymessenger.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import java.util.List;

/**
 * Сервис для работы с чатом
 * Service for chat operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    /**
     * Получение пользователя по username
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found: {}", username);
                    return new RuntimeException("User not found: " + username);
                });
    }

    /**
     * Сохранение сообщения в комнате чата
     * Save message in chat room
     */
    @Transactional
    public Message saveMessage(String content, String roomId, User sender, Message.MessageType type) {

        log.debug("Saving message from {} in room {}", sender.getUsername(), roomId);

        // Находим комнату чата
        // Find chat room
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> {
                    log.error("Chat room not found: {}", roomId);
                    return new RuntimeException("Chat room not found: " + roomId);
                });

        // Создаём новое сообщение
        // Create new message
        Message message = new Message();
        message.setContent(content);
        message.setChatRoom(chatRoom);
        message.setSender(sender);
        message.setType(type != null ? type : Message.MessageType.TEXT);

        // Сохраняем в базу данных
        // Save to database
        Message savedMessage = messageRepository.save(message);

        log.info("Message saved with id: {}", savedMessage.getId());

        return savedMessage;
    }

    /**
     * Сохранение личного сообщения
     * Save private message
     */
    @Transactional
    public Message savePrivateMessage(String content, String recipientUsername, User sender) {

        log.debug("Saving private message from {} to {}", sender.getUsername(), recipientUsername);

        // Находим получателя
        // Find recipient
        User recipient = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> {
                    log.error("Recipient not found: {}", recipientUsername);
                    return new RuntimeException("Recipient not found: " + recipientUsername);
                });

        // Создаём или находим личный чат между пользователями
        // Create or find direct chat between users
        ChatRoom directChat = chatRoomRepository.findDirectChatBetweenUsers(sender, recipient)
                .orElseGet(() -> createDirectChat(sender, recipient));

        // Создаём сообщение
        // Create message
        Message message = new Message();
        message.setContent(content);
        message.setChatRoom(directChat);
        message.setSender(sender);
        message.setType(Message.MessageType.TEXT);

        return messageRepository.save(message);
    }

    /**
     * Создание личного чата между двумя пользователями
     * Create direct chat between two users
     */
    private ChatRoom createDirectChat(User user1, User user2) {

        log.info("Creating direct chat between {} and {}", user1.getUsername(), user2.getUsername());

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(user1.getUsername() + "-" + user2.getUsername());
        chatRoom.setType(ChatRoom.RoomType.DIRECT);
        chatRoom.setCreatedBy(user1);
        chatRoom.getParticipants().add(user1);
        chatRoom.getParticipants().add(user2);

        return chatRoomRepository.save(chatRoom);
    }

    /**
     * Получение истории сообщений в комнате
     * Get message history in room
     */
    public List<Message> getMessageHistory(String roomId, int limit) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        return messageRepository.findByChatRoomOrderByTimestampDesc(chatRoom, Pageable.ofSize(limit));
    }
}