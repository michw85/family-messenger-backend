package com.familymessenger.backend.service;

import com.familymessenger.backend.dto.ReactionSummaryDto;
import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.entity.MessageReaction;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.ChatRoomRepository;
import com.familymessenger.backend.repository.MessageReactionRepository;
import com.familymessenger.backend.repository.MessageRepository;
import com.familymessenger.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    private final MessageReactionRepository messageReactionRepository;

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
    public Message saveMessage(String content, String roomId, User sender, Message.MessageType type, String mediaUrl, String replyToId, LocalDateTime revealAt) {

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
        message.setRevealAt(revealAt);

        // Если это ответ - находим оригинал, но только если он из того же чата
        // If this is a reply - look up the original, but only if it's from the same chat
        if (replyToId != null && !replyToId.isBlank()) {
            messageRepository.findById(replyToId)
                    .filter(original -> original.getChatRoom().getId().equals(roomId))
                    .ifPresent(message::setReplyTo);
        }

        // Новое сообщение "возвращает" чат тем, кто ранее удалил его у себя
        // A new message "brings back" the chat for anyone who'd deleted it for themselves
        if (!chatRoom.getHiddenForUserIds().isEmpty()) {
            chatRoom.getHiddenForUserIds().clear();
            chatRoomRepository.save(chatRoom);
        }

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
     * Получение истории сообщений в комнате постранично (страница 0 - самые
     * новые сообщения, дальше - всё более старые)
     * Get message history in room, paginated (page 0 - the newest messages,
     * higher pages - progressively older ones)
     */
    public List<Message> getMessageHistory(String roomId, int page, int size) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        return messageRepository.findByChatRoomOrderByTimestampDesc(chatRoom, PageRequest.of(page, size));
    }

    /**
     * Поиск по тексту сообщений внутри чата (без учёта регистра)
     * Search message content within a chat (case-insensitive)
     */
    @Transactional(readOnly = true)
    public List<Message> searchMessages(String roomId, String query) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        return messageRepository.findByChatRoomAndContentContainingIgnoreCaseOrderByTimestampDesc(chatRoom, query);
    }

    /**
     * Получить все чаты, в которых участвует пользователь
     * Get all chats where user participates
     */
    public List<ChatRoom> getChatsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return chatRoomRepository.findByParticipantsContaining(user).stream()
                .filter(chat -> !chat.getHiddenForUserIds().contains(userId))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Время последнего сообщения в чате (для сортировки списка чатов по
     * активности) - если сообщений ещё нет, используется дата создания чата
     * Timestamp of the chat's last message (for sorting the chat list by
     * activity) - falls back to the chat's creation date if there are no
     * messages yet
     */
    @Transactional(readOnly = true)
    public LocalDateTime getLastActivityTimestamp(ChatRoom chatRoom) {
        return messageRepository.findFirstByChatRoomOrderByTimestampDesc(chatRoom)
                .map(Message::getTimestamp)
                .orElse(chatRoom.getCreatedAt());
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
     * Покинуть/удалить чат для себя.
     * В групповых чатах - выходим из числа участников (как и раньше умел
     * делать removeParticipant для самого себя). В личных чатах (DIRECT/FAMILY)
     * участника нельзя просто убрать - там чат лишь скрывается у текущего
     * пользователя (и снова появится, если придёт новое сообщение).
     * Если после этого чат не виден никому из участников, он удаляется целиком.
     *
     * Leave/delete a chat for yourself.
     * In group chats - leave the participant list (same behavior
     * removeParticipant already supported for self-removal). In personal
     * chats (DIRECT/FAMILY) a participant can't just be removed - the chat
     * is instead hidden for the current user only (and reappears if a new
     * message arrives). If nobody can see the chat anymore afterwards, it's
     * deleted entirely.
     *
     * @param chatId - ID чата / chat ID
     * @param userId - ID текущего пользователя / current user ID
     */
    @Transactional
    public void leaveChat(String chatId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Not a participant of this chat");
        }

        if (chatRoom.getType() == ChatRoom.RoomType.GROUP) {
            chatRoom.getParticipants().removeIf(u -> u.getId().equals(userId));
            if (chatRoom.getParticipants().isEmpty()) {
                chatRoomRepository.delete(chatRoom);
                log.info("Chat {} deleted - last participant left", chatId);
                return;
            }
        } else {
            chatRoom.getHiddenForUserIds().add(userId);
            boolean hiddenForEveryone = chatRoom.getParticipants().stream()
                    .allMatch(u -> chatRoom.getHiddenForUserIds().contains(u.getId()));
            if (hiddenForEveryone) {
                chatRoomRepository.delete(chatRoom);
                log.info("Chat {} deleted - hidden for all participants", chatId);
                return;
            }
        }

        chatRoomRepository.save(chatRoom);
        log.info("User {} left/hid chat {}", userId, chatId);
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
     * Редактирование собственного сообщения
     * Editing your own message
     */
    @Transactional
    public Message editMessage(String messageId, String newContent, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new RuntimeException("Only the sender can edit this message");
        }
        if (message.isDeleted()) {
            throw new RuntimeException("Cannot edit a deleted message");
        }

        message.setContent(newContent);
        message.setEdited(true);
        return messageRepository.save(message);
    }

    /**
     * Удаление собственного сообщения (мягкое - оставляет плейсхолдер)
     * Deleting your own message (soft delete - leaves a placeholder)
     */
    @Transactional
    public Message deleteMessage(String messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getSender().getId().equals(userId)) {
            throw new RuntimeException("Only the sender can delete this message");
        }

        message.setDeleted(true);
        message.setContent(null);
        message.setMediaUrl(null);
        return messageRepository.save(message);
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
                // Не шлём пуш тем, кто заглушил этот чат / Skip push for anyone who muted this chat
                .filter(u -> !chatRoom.getMutedForUserIds().contains(u.getId()))
                .map(User::getFcmToken)
                .filter(token -> token != null && !token.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Заглушить/включить уведомления по чату для текущего пользователя
     * Mute/unmute push notifications for this chat for the current user
     *
     * @param chatId - ID чата / chat ID
     * @param userId - ID пользователя / user ID
     * @param muted - true, чтобы заглушить, false - чтобы включить обратно / true to mute, false to unmute
     */
    /**
     * Отметить чат прочитанным текущим пользователем (до настоящего момента)
     * Mark the chat as read by the current user (up to now)
     *
     * @param chatId - ID чата / chat ID
     * @param userId - ID пользователя / user ID
     * @return время, которое было записано как "прочитано до" / the timestamp recorded as "read up to"
     */
    @Transactional
    public LocalDateTime markChatRead(String chatId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Not a participant of this chat");
        }

        LocalDateTime now = LocalDateTime.now();
        chatRoom.getLastReadAt().put(userId, now);
        chatRoomRepository.save(chatRoom);
        return now;
    }

    /**
     * Прочитано ли сообщение всеми остальными участниками чата (не считая отправителя)
     * Whether the message has been read by every other participant of the chat (excluding the sender)
     */
    public boolean isReadByAllOthers(ChatRoom chatRoom, Message message) {
        Long senderId = message.getSender() != null ? message.getSender().getId() : null;
        return chatRoom.getParticipants().stream()
                .filter(p -> !p.getId().equals(senderId))
                .allMatch(p -> {
                    LocalDateTime lastRead = chatRoom.getLastReadAt().get(p.getId());
                    return lastRead != null && !lastRead.isBefore(message.getTimestamp());
                });
    }

    /**
     * Получить чат по ID
     * Get chat room by ID
     */
    @Transactional(readOnly = true)
    public ChatRoom getChatRoomById(String chatId) {
        return chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
    }

    /**
     * "Лента памяти": сообщения из этого чата, отправленные в этот же день в
     * прошлые годы ("год назад в этот день")
     * "Memory lane": messages from this chat sent on this same day in past
     * years ("a year ago today")
     */
    @Transactional(readOnly = true)
    public List<Message> getMemories(String chatId) {
        ChatRoom chatRoom = getChatRoomById(chatId);
        LocalDate today = LocalDate.now();
        return messageRepository.findOnThisDayInPast(chatRoom, today.getMonthValue(), today.getDayOfMonth(), today.getYear());
    }

    @Transactional
    public void setChatMuted(String chatId, Long userId, boolean muted) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Not a participant of this chat");
        }

        if (muted) {
            chatRoom.getMutedForUserIds().add(userId);
        } else {
            chatRoom.getMutedForUserIds().remove(userId);
        }
        chatRoomRepository.save(chatRoom);
    }

    /**
     * Переименовать чат - доступно любому участнику (личный чат в этом
     * приложении хранит одно общее название на обоих участников, а не
     * персональный псевдоним каждого, как в некоторых мессенджерах - см.
     * ChatRoom.name), не только создателю, в отличие от полного удаления.
     * Rename a chat - available to any participant (a personal chat in this
     * app stores one shared name for both participants, not a per-user
     * nickname like in some messengers - see ChatRoom.name), not just the
     * creator, unlike full deletion.
     */
    public ChatRoom renameChat(String chatId, String newName, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        boolean isParticipant = chatRoom.getParticipants().stream()
                .anyMatch(u -> u.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Not a participant of this chat");
        }

        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            throw new RuntimeException("Chat name cannot be empty");
        }

        chatRoom.setName(trimmed);
        return chatRoomRepository.save(chatRoom);
    }

    /**
     * Поставить/снять/заменить реакцию текущего пользователя на сообщение.
     * Повторный выбор той же эмодзи снимает реакцию, выбор другой - заменяет.
     * Toggle the current user's reaction on a message. Picking the same
     * emoji again removes it, picking a different one replaces it.
     *
     * @param messageId - ID сообщения / message ID
     * @param userId - ID пользователя / user ID
     * @param emoji - эмодзи реакции / reaction emoji
     * @return сообщение, к которому относится реакция / the message the reaction belongs to
     */
    @Transactional
    public Message toggleReaction(String messageId, Long userId, String emoji) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isParticipant = message.getChatRoom().getParticipants().stream()
                .anyMatch(p -> p.getId().equals(userId));
        if (!isParticipant) {
            throw new RuntimeException("Not a participant of this chat");
        }

        messageReactionRepository.findByMessageAndUser(message, user).ifPresentOrElse(existing -> {
            if (existing.getEmoji().equals(emoji)) {
                messageReactionRepository.delete(existing);
            } else {
                existing.setEmoji(emoji);
                messageReactionRepository.save(existing);
            }
        }, () -> {
            MessageReaction reaction = new MessageReaction();
            reaction.setMessage(message);
            reaction.setUser(user);
            reaction.setEmoji(emoji);
            messageReactionRepository.save(reaction);
        });

        return message;
    }

    /**
     * Сводка реакций на сообщение, сгруппированных по эмодзи. Не зависит от
     * конкретного зрителя - годится и для REST-ответа, и для WS-рассылки всем.
     * Reaction summary for a message, grouped by emoji. Not viewer-specific -
     * works for both the REST response and the WebSocket broadcast to everyone.
     *
     * @param message - сообщение / message
     */
    @Transactional(readOnly = true)
    public List<ReactionSummaryDto> getReactionSummary(Message message) {
        List<MessageReaction> reactions = messageReactionRepository.findByMessage(message);
        java.util.Map<String, List<MessageReaction>> byEmoji = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getEmoji));

        return byEmoji.entrySet().stream()
                .map(entry -> ReactionSummaryDto.builder()
                        .emoji(entry.getKey())
                        .count(entry.getValue().size())
                        .usernames(entry.getValue().stream()
                                .map(r -> r.getUser().getUsername())
                                .collect(Collectors.toList()))
                        .build())
                .sorted(Comparator.comparing(ReactionSummaryDto::getEmoji))
                .collect(Collectors.toList());
    }
}