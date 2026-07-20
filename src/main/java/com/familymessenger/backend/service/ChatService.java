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
    public Message saveMessage(String content, String roomId, User sender, Message.MessageType type, String mediaUrl) {

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
        message.setMediaUrl(mediaUrl);

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

    /**
     * Получить все чаты, в которых участвует пользователь
     * Get all chats where user participates
     */
    public List<ChatRoom> getChatsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return chatRoomRepository.findByParticipantsContaining(user);
    }

    /**
     * Создать новый чат (групповой/семейный)
     * Create new group/family chat
     */
    @Transactional
    public ChatRoom createChat(String name, ChatRoom.RoomType type, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(name);
        chatRoom.setType(type);
        chatRoom.setCreatedBy(creator);
        chatRoom.getParticipants().add(creator);

        return chatRoomRepository.save(chatRoom);
    }

    /**
     * Удалить чат, если пользователь является создателем
     * Delete chat if user is creator
     */
    @Transactional
    public void deleteChat(String chatId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        if (!chatRoom.getCreatedBy().getId().equals(userId)) {
            throw new RuntimeException("Only creator can delete chat");
        }

        // Можно также удалить все сообщения (каскадно, если настроено в JPA)
        chatRoomRepository.delete(chatRoom);
        log.info("Chat deleted: {}", chatId);
    }

    public List<User> getParticipants(String chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        return chatRoom.getParticipants();
    }

    /**
     * Добавить участников в чат
     * Add participants to chat
     * @param chatId - ID чата / chat ID
     * @param userIds - ID пользователей для добавления / user IDs to add
     * @param currentUserId - ID текущего пользователя / current user ID
     * @return обновлённый список участников / updated list of participants
     */
    @Transactional
    public List<User> addParticipants(String chatId, List<Long> userIds, Long currentUserId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        // Проверяем, что текущий пользователь является участником / Check current user is participant
        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(currentUserId));
        if (!isParticipant) {
            throw new RuntimeException("Only participants can add others");
        }

        // Загружаем пользователей для добавления / Load users to add
        List<User> usersToAdd = userRepository.findAllById(userIds);
        for (User user : usersToAdd) {
            if (!chatRoom.getParticipants().contains(user)) {
                chatRoom.getParticipants().add(user);
            }
        }

        chatRoomRepository.save(chatRoom);
        return chatRoom.getParticipants();
    }

    /**
     * Удалить участника из чата
     * Remove participant from chat
     * @param chatId - ID чата / chat ID
     * @param userId - ID пользователя для удаления / user ID to remove
     * @param currentUserId - ID текущего пользователя / current user ID
     */
    @Transactional
    public void removeParticipant(String chatId, Long userId, Long currentUserId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        // Только создатель или сам участник может удалить / Only creator or the participant can remove
        boolean isCreator = chatRoom.getCreatedBy().getId().equals(currentUserId);
        boolean isSelf = userId.equals(currentUserId);

        if (!isCreator && !isSelf) {
            throw new RuntimeException("Only creator or the participant can remove themself");
        }

        User userToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        chatRoom.getParticipants().remove(userToRemove);
        chatRoomRepository.save(chatRoom);
    }

    /**
     * Создать групповой чат с участниками
     * Create group chat with participants
     * @param name - название чата / chat name
     * @param creatorId - ID создателя / creator ID
     * @param participantIds - ID участников / participant IDs
     * @return созданный чат / created chat
     */
    @Transactional
    public ChatRoom createGroupChat(String name, Long creatorId, List<Long> participantIds) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(name);
        chatRoom.setType(ChatRoom.RoomType.GROUP);
        chatRoom.setCreatedBy(creator);
        chatRoom.getParticipants().add(creator);

        // Добавляем участников / Add participants
        if (participantIds != null && !participantIds.isEmpty()) {
            List<User> participants = userRepository.findAllById(participantIds);
            for (User user : participants) {
                if (!chatRoom.getParticipants().contains(user)) {
                    chatRoom.getParticipants().add(user);
                }
            }
        }

        return chatRoomRepository.save(chatRoom);
    }


    /**
     * Проверка, является ли пользователь участником чата
     * Check whether the user is a participant of the chat room
     */
    @Transactional(readOnly = true)
    public boolean isParticipant(String chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        return chatRoom.getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
    }

    /**
     * Проверка, имеет ли пользователь доступ к медиафайлу
     * (файл должен принадлежать сообщению в чате, где пользователь — участник)
     * Check if user has access to a media file (file must belong to a message
     * in a chat room where the user is a participant)
     */
    @Transactional(readOnly = true)
    public boolean canAccessMediaFile(String filename, Long userId) {
        return messageRepository.findFirstByMediaUrlEndingWith(filename)
                .map(m -> m.getChatRoom().getParticipants().stream()
                        .anyMatch(p -> p.getId().equals(userId)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> getParticipantFcmTokens(String chatRoomId, Long excludeUserId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        return chatRoom.getParticipants().stream()
                .filter(u -> !u.getId().equals(excludeUserId))
                .map(User::getFcmToken)
                .filter(token -> token != null && !token.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}