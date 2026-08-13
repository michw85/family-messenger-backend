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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Тесты на "не-ролевую" бизнес-логику ChatService - создание/чтение/удаление
 * сообщений и чатов, реакции, отметки о прочтении и т.д. Ролевая модель
 * (суперадмин/админ группы/редактор/чёрный список) покрыта отдельно в
 * ChatServiceRolesTest.
 * Tests for ChatService's non-role business logic - creating/reading/deleting
 * messages and chats, reactions, read receipts, etc. The role model
 * (superadmin/group admin/editor/blacklist) is covered separately in
 * ChatServiceRolesTest.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceCoreTest {

    private static final String CHAT_ID = "chat-1";

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;

    private ChatService chatService;

    private User alice;
    private User bob;
    private ChatRoom groupChat;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(messageRepository, chatRoomRepository, userRepository, messageReactionRepository);

        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");

        bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");

        groupChat = new ChatRoom();
        groupChat.setId(CHAT_ID);
        groupChat.setName("Family");
        groupChat.setType(ChatRoom.RoomType.GROUP);
        groupChat.setCreatedBy(alice);
        groupChat.getParticipants().add(alice);
        groupChat.getParticipants().add(bob);
    }

    private void stubMessageSaveReturnsArgument() {
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubChatRoomSaveReturnsArgument() {
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- saveMessage ------------------------------------------------------

    @Test
    void saveMessage_savesWithSenderAndDefaultsTypeToTextWhenNull() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        stubMessageSaveReturnsArgument();

        Message saved = chatService.saveMessage("hello", CHAT_ID, alice, null, null, null, null);

        assertEquals("hello", saved.getContent());
        assertEquals(alice, saved.getSender());
        assertEquals(Message.MessageType.TEXT, saved.getType());
        assertEquals(groupChat, saved.getChatRoom());
    }

    @Test
    void saveMessage_aNewMessageBringsTheChatBackForAnyoneWhoHadHiddenIt() {
        groupChat.getHiddenForUserIds().add(2L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        stubMessageSaveReturnsArgument();
        stubChatRoomSaveReturnsArgument();

        chatService.saveMessage("hello", CHAT_ID, alice, Message.MessageType.TEXT, null, null, null);

        assertTrue(groupChat.getHiddenForUserIds().isEmpty());
        verify(chatRoomRepository).save(groupChat);
    }

    @Test
    void saveMessage_doesNotTouchChatRoomWhenNobodyHadHiddenIt() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        chatService.saveMessage("hello", CHAT_ID, alice, Message.MessageType.TEXT, null, null, null);

        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void saveMessage_linksReplyToOnlyWhenTheOriginalIsFromTheSameChat() {
        Message original = new Message();
        original.setId("msg-1");
        original.setChatRoom(groupChat);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(original));
        stubMessageSaveReturnsArgument();

        Message saved = chatService.saveMessage("a reply", CHAT_ID, alice, Message.MessageType.TEXT, null, "msg-1", null);

        assertEquals(original, saved.getReplyTo());
    }

    @Test
    void saveMessage_ignoresAReplyToPointingAtAMessageFromAnotherChat() {
        ChatRoom otherChat = new ChatRoom();
        otherChat.setId("chat-2");
        Message originalElsewhere = new Message();
        originalElsewhere.setId("msg-1");
        originalElsewhere.setChatRoom(otherChat);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(originalElsewhere));
        stubMessageSaveReturnsArgument();

        Message saved = chatService.saveMessage("a reply", CHAT_ID, alice, Message.MessageType.TEXT, null, "msg-1", null);

        assertNull(saved.getReplyTo());
    }

    @Test
    void saveMessage_throwsWhenTheChatDoesNotExist() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> chatService.saveMessage("hi", CHAT_ID, alice, Message.MessageType.TEXT, null, null, null));
    }

    // --- savePrivateMessage / createDirectChat -----------------------------

    @Test
    void savePrivateMessage_reusesAnExistingDirectChatBetweenTheTwoUsers() {
        ChatRoom existingDirect = new ChatRoom();
        existingDirect.setId("direct-1");
        existingDirect.setType(ChatRoom.RoomType.DIRECT);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(chatRoomRepository.findDirectChatBetweenUsers(alice, bob)).thenReturn(Optional.of(existingDirect));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message saved = chatService.savePrivateMessage("hi bob", "bob", alice);

        assertEquals(existingDirect, saved.getChatRoom());
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void savePrivateMessage_createsANewDirectChatWhenNoneExistsYet() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(chatRoomRepository.findDirectChatBetweenUsers(alice, bob)).thenReturn(Optional.empty());
        stubMessageSaveReturnsArgument();
        stubChatRoomSaveReturnsArgument();

        Message saved = chatService.savePrivateMessage("hi bob", "bob", alice);

        ChatRoom created = saved.getChatRoom();
        assertEquals(ChatRoom.RoomType.DIRECT, created.getType());
        assertTrue(created.getParticipants().contains(alice));
        assertTrue(created.getParticipants().contains(bob));
        verify(chatRoomRepository).save(created);
    }

    @Test
    void savePrivateMessage_throwsWhenRecipientDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> chatService.savePrivateMessage("hi", "ghost", alice));
    }

    // --- getMessageHistory / searchMessages --------------------------------

    @Test
    void getMessageHistory_throwsWhenTheChatDoesNotExist() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> chatService.getMessageHistory(CHAT_ID, 0, 20));
    }

    @Test
    void searchMessages_throwsWhenTheChatDoesNotExist() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> chatService.searchMessages(CHAT_ID, "hello"));
    }

    // --- getChatsForUser ----------------------------------------------------

    @Test
    void getChatsForUser_excludesChatsHiddenForThatUser() {
        ChatRoom hiddenForAlice = new ChatRoom();
        hiddenForAlice.setId("chat-2");
        hiddenForAlice.getHiddenForUserIds().add(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(chatRoomRepository.findByParticipantsContaining(alice)).thenReturn(List.of(groupChat, hiddenForAlice));

        List<ChatRoom> result = chatService.getChatsForUser(1L);

        assertEquals(List.of(groupChat), result);
    }

    // --- getLastActivityTimestamp -------------------------------------------

    @Test
    void getLastActivityTimestamp_usesTheLastMessageTimestampWhenThereIsOne() {
        LocalDateTime lastMsgTime = LocalDateTime.now().minusHours(1);
        Message lastMessage = new Message();
        lastMessage.setTimestamp(lastMsgTime);
        when(messageRepository.findFirstByChatRoomOrderByTimestampDesc(groupChat)).thenReturn(Optional.of(lastMessage));

        assertEquals(lastMsgTime, chatService.getLastActivityTimestamp(groupChat));
    }

    @Test
    void getLastActivityTimestamp_fallsBackToChatCreationDateWhenThereAreNoMessagesYet() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(3);
        groupChat.setCreatedAt(createdAt);
        when(messageRepository.findFirstByChatRoomOrderByTimestampDesc(groupChat)).thenReturn(Optional.empty());

        assertEquals(createdAt, chatService.getLastActivityTimestamp(groupChat));
    }

    // --- createChat / createGroupChat ---------------------------------------

    @Test
    void createChat_setsTheCreatorAsTheSoleParticipant() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        stubChatRoomSaveReturnsArgument();

        ChatRoom created = chatService.createChat("Notes", ChatRoom.RoomType.GROUP, 1L);

        assertEquals(List.of(alice), created.getParticipants());
        assertEquals(alice, created.getCreatedBy());
    }

    @Test
    void createGroupChat_addsCreatorAndParticipantsWithoutDuplicatingTheCreator() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        // Создатель по ошибке снова передан в списке участников
        // Creator mistakenly passed again in the participants list
        when(userRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(alice, bob));
        stubChatRoomSaveReturnsArgument();

        ChatRoom created = chatService.createGroupChat("Family", 1L, List.of(1L, 2L));

        assertEquals(2, created.getParticipants().size());
        assertTrue(created.getParticipants().contains(alice));
        assertTrue(created.getParticipants().contains(bob));
    }

    // --- addParticipants -----------------------------------------------------

    @Test
    void addParticipants_rejectedForAPersonalDirectChatEvenByAParticipant() {
        ChatRoom directChat = new ChatRoom();
        directChat.setId("direct-1");
        directChat.setType(ChatRoom.RoomType.DIRECT);
        directChat.getParticipants().add(alice);
        directChat.getParticipants().add(bob);
        when(chatRoomRepository.findById("direct-1")).thenReturn(Optional.of(directChat));

        assertThrows(RuntimeException.class, () -> chatService.addParticipants("direct-1", List.of(3L), 1L));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void addParticipants_onlyAnExistingParticipantCanAddOthers() {
        User outsider = new User();
        outsider.setId(99L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.addParticipants(CHAT_ID, List.of(3L), 99L));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void addParticipants_doesNotAddTheSamePersonTwice() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(bob));
        stubChatRoomSaveReturnsArgument();

        List<User> result = chatService.addParticipants(CHAT_ID, List.of(2L), 1L);

        assertEquals(2, result.size());
    }

    @Test
    void addParticipants_addsANewUser() {
        User carol = new User();
        carol.setId(3L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findAllById(List.of(3L))).thenReturn(List.of(carol));
        stubChatRoomSaveReturnsArgument();

        List<User> result = chatService.addParticipants(CHAT_ID, List.of(3L), 1L);

        assertTrue(result.contains(carol));
        assertEquals(3, result.size());
    }

    // --- leaveChat -------------------------------------------------------

    @Test
    void leaveChat_throwsWhenTheCallerIsNotAParticipant() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.leaveChat(CHAT_ID, 99L));
    }

    @Test
    void leaveChat_groupChatJustRemovesTheLeavingParticipantWhenOthersRemain() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.leaveChat(CHAT_ID, 2L);

        assertFalse(groupChat.getParticipants().contains(bob));
        verify(chatRoomRepository).save(groupChat);
        verify(messageRepository, never()).deleteByChatRoom(any());
    }

    @Test
    void leaveChat_groupChatIsDeletedEntirelyWhenTheLastParticipantLeaves() {
        groupChat.getParticipants().remove(bob);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.leaveChat(CHAT_ID, 1L);

        verify(messageReactionRepository).deleteByMessageChatRoom(groupChat);
        verify(messageRepository).deleteByChatRoom(groupChat);
        verify(chatRoomRepository).delete(groupChat);
    }

    @Test
    void leaveChat_personalChatIsOnlyHiddenForTheLeavingUserWhileTheOtherStillSeesIt() {
        ChatRoom directChat = new ChatRoom();
        directChat.setId("direct-1");
        directChat.setType(ChatRoom.RoomType.DIRECT);
        directChat.getParticipants().add(alice);
        directChat.getParticipants().add(bob);
        when(chatRoomRepository.findById("direct-1")).thenReturn(Optional.of(directChat));

        chatService.leaveChat("direct-1", 1L);

        assertTrue(directChat.getHiddenForUserIds().contains(1L));
        assertTrue(directChat.getParticipants().contains(alice));
        verify(chatRoomRepository).save(directChat);
        verify(chatRoomRepository, never()).delete(any());
    }

    // --- editMessage / deleteMessage ----------------------------------------

    @Test
    void editMessage_theSenderCanEditTheirOwnMessage() {
        Message message = new Message();
        message.setId("msg-1");
        message.setSender(alice);
        message.setContent("original");
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message edited = chatService.editMessage("msg-1", "updated", 1L);

        assertEquals("updated", edited.getContent());
        assertTrue(edited.isEdited());
    }

    @Test
    void editMessage_onlyTheSenderCanEditIt() {
        Message message = new Message();
        message.setId("msg-1");
        message.setSender(alice);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        assertThrows(RuntimeException.class, () -> chatService.editMessage("msg-1", "updated", 2L));
    }

    @Test
    void editMessage_cannotEditAnAlreadyDeletedMessage() {
        Message message = new Message();
        message.setId("msg-1");
        message.setSender(alice);
        message.setDeleted(true);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        assertThrows(RuntimeException.class, () -> chatService.editMessage("msg-1", "updated", 1L));
    }

    @Test
    void deleteMessage_softDeletesAndClearsContentAndMedia() {
        Message message = new Message();
        message.setId("msg-1");
        message.setSender(alice);
        message.setContent("secret");
        message.setMediaUrl("https://example.com/file.jpg");
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message deleted = chatService.deleteMessage("msg-1", 1L);

        assertTrue(deleted.isDeleted());
        assertNull(deleted.getContent());
        assertNull(deleted.getMediaUrl());
    }

    @Test
    void deleteMessage_onlyTheSenderCanDeleteIt() {
        Message message = new Message();
        message.setId("msg-1");
        message.setSender(alice);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));

        assertThrows(RuntimeException.class, () -> chatService.deleteMessage("msg-1", 2L));
    }

    // --- isParticipant / canAccessMediaFile ----------------------------------

    @Test
    void isParticipant_trueForAMemberOfTheChat() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertTrue(chatService.isParticipant(CHAT_ID, 1L));
    }

    @Test
    void isParticipant_falseForSomeoneNotInTheChat() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertFalse(chatService.isParticipant(CHAT_ID, 99L));
    }

    @Test
    void canAccessMediaFile_trueWhenTheFileBelongsToAMessageInAChatTheUserIsIn() {
        Message message = new Message();
        message.setChatRoom(groupChat);
        when(messageRepository.findFirstByMediaUrlEndingWith("photo.jpg")).thenReturn(Optional.of(message));

        assertTrue(chatService.canAccessMediaFile("photo.jpg", 1L));
    }

    @Test
    void canAccessMediaFile_falseWhenTheUserIsNotAParticipantOfThatChat() {
        Message message = new Message();
        message.setChatRoom(groupChat);
        when(messageRepository.findFirstByMediaUrlEndingWith("photo.jpg")).thenReturn(Optional.of(message));

        assertFalse(chatService.canAccessMediaFile("photo.jpg", 99L));
    }

    @Test
    void canAccessMediaFile_falseWhenNoMessageReferencesThatFile() {
        when(messageRepository.findFirstByMediaUrlEndingWith("photo.jpg")).thenReturn(Optional.empty());

        assertFalse(chatService.canAccessMediaFile("photo.jpg", 1L));
    }

    // --- getParticipantFcmTokens ---------------------------------------------

    @Test
    void getParticipantFcmTokens_excludesTheSenderMutedUsersAndEmptyTokens() {
        alice.setFcmToken("alice-token");
        bob.setFcmToken("bob-token");
        User carol = new User();
        carol.setId(3L);
        carol.setFcmToken(null);
        groupChat.getParticipants().add(carol);
        groupChat.getMutedForUserIds().add(2L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        List<String> tokens = chatService.getParticipantFcmTokens(CHAT_ID, 1L);

        assertTrue(tokens.isEmpty());
    }

    @Test
    void getParticipantFcmTokens_includesAnUnmutedOtherParticipantsToken() {
        alice.setFcmToken("alice-token");
        bob.setFcmToken("bob-token");
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        List<String> tokens = chatService.getParticipantFcmTokens(CHAT_ID, 1L);

        assertEquals(List.of("bob-token"), tokens);
    }

    // --- markChatRead / isReadByAllOthers -------------------------------------

    @Test
    void markChatRead_throwsForANonParticipant() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.markChatRead(CHAT_ID, 99L));
    }

    @Test
    void markChatRead_recordsANearCurrentTimestampForTheParticipant() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        LocalDateTime before = LocalDateTime.now();
        LocalDateTime recorded = chatService.markChatRead(CHAT_ID, 1L);

        assertEquals(recorded, groupChat.getLastReadAt().get(1L));
        assertFalse(recorded.isBefore(before));
    }

    @Test
    void isReadByAllOthers_trueWhenEveryoneElseHasReadPastTheMessage() {
        Message message = new Message();
        message.setSender(alice);
        message.setTimestamp(LocalDateTime.now().minusMinutes(5));
        groupChat.getLastReadAt().put(2L, LocalDateTime.now());

        assertTrue(chatService.isReadByAllOthers(groupChat, message));
    }

    @Test
    void isReadByAllOthers_falseWhenSomeoneHasNotCaughtUpYet() {
        Message message = new Message();
        message.setSender(alice);
        message.setTimestamp(LocalDateTime.now());
        groupChat.getLastReadAt().put(2L, LocalDateTime.now().minusHours(1));

        assertFalse(chatService.isReadByAllOthers(groupChat, message));
    }

    // --- getMemories -----------------------------------------------------

    @Test
    void getMemories_queriesUsingTodaysMonthAndDay() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(messageRepository.findOnThisDayInPast(eq(groupChat), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        chatService.getMemories(CHAT_ID);

        LocalDate today = LocalDate.now();
        verify(messageRepository).findOnThisDayInPast(groupChat, today.getMonthValue(), today.getDayOfMonth(), today.getYear());
    }

    // --- setChatMuted -----------------------------------------------------

    @Test
    void setChatMuted_addsTheUserToTheMutedSet() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.setChatMuted(CHAT_ID, 1L, true);

        assertTrue(groupChat.getMutedForUserIds().contains(1L));
    }

    @Test
    void setChatMuted_removesTheUserFromTheMutedSet() {
        groupChat.getMutedForUserIds().add(1L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.setChatMuted(CHAT_ID, 1L, false);

        assertFalse(groupChat.getMutedForUserIds().contains(1L));
    }

    @Test
    void setChatMuted_throwsForANonParticipant() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.setChatMuted(CHAT_ID, 99L, true));
    }

    // --- renameChat -----------------------------------------------------

    @Test
    void renameChat_trimsWhitespaceAndSavesTheNewName() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        stubChatRoomSaveReturnsArgument();

        ChatRoom renamed = chatService.renameChat(CHAT_ID, "  New Name  ", 1L);

        assertEquals("New Name", renamed.getName());
    }

    @Test
    void renameChat_rejectsABlankName() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.renameChat(CHAT_ID, "   ", 1L));
    }

    @Test
    void renameChat_onlyAParticipantCanRenameIt() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class, () -> chatService.renameChat(CHAT_ID, "New Name", 99L));
    }

    // --- toggleReaction / getReactionSummary ----------------------------------

    @Test
    void toggleReaction_addsANewReactionWhenTheUserHasNoneYet() {
        Message message = new Message();
        message.setId("msg-1");
        message.setChatRoom(groupChat);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(messageReactionRepository.findByMessageAndUser(message, alice)).thenReturn(Optional.empty());

        chatService.toggleReaction("msg-1", 1L, "👍");

        ArgumentCaptor<MessageReaction> captor = ArgumentCaptor.forClass(MessageReaction.class);
        verify(messageReactionRepository).save(captor.capture());
        assertEquals("👍", captor.getValue().getEmoji());
        assertEquals(alice, captor.getValue().getUser());
    }

    @Test
    void toggleReaction_removesTheReactionWhenTheSameEmojiIsPickedAgain() {
        Message message = new Message();
        message.setId("msg-1");
        message.setChatRoom(groupChat);
        MessageReaction existing = new MessageReaction();
        existing.setEmoji("👍");
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(messageReactionRepository.findByMessageAndUser(message, alice)).thenReturn(Optional.of(existing));

        chatService.toggleReaction("msg-1", 1L, "👍");

        verify(messageReactionRepository).delete(existing);
        verify(messageReactionRepository, never()).save(any());
    }

    @Test
    void toggleReaction_replacesTheReactionWhenADifferentEmojiIsPicked() {
        Message message = new Message();
        message.setId("msg-1");
        message.setChatRoom(groupChat);
        MessageReaction existing = new MessageReaction();
        existing.setEmoji("👍");
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(messageReactionRepository.findByMessageAndUser(message, alice)).thenReturn(Optional.of(existing));

        chatService.toggleReaction("msg-1", 1L, "❤️");

        assertEquals("❤️", existing.getEmoji());
        verify(messageReactionRepository).save(existing);
        verify(messageReactionRepository, never()).delete(any());
    }

    @Test
    void toggleReaction_throwsWhenTheUserIsNotAParticipantOfTheChat() {
        Message message = new Message();
        message.setId("msg-1");
        message.setChatRoom(groupChat);
        when(messageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(userRepository.findById(99L)).thenReturn(Optional.of(new User()));

        assertThrows(RuntimeException.class, () -> chatService.toggleReaction("msg-1", 99L, "👍"));
    }

    @Test
    void getReactionSummary_groupsByEmojiWithCountsAndUsernamesSortedByEmoji() {
        Message message = new Message();
        message.setId("msg-1");

        MessageReaction thumbsUpFromAlice = new MessageReaction();
        thumbsUpFromAlice.setEmoji("👍");
        thumbsUpFromAlice.setUser(alice);

        MessageReaction heartFromBob = new MessageReaction();
        heartFromBob.setEmoji("❤️");
        heartFromBob.setUser(bob);

        when(messageReactionRepository.findByMessage(message)).thenReturn(List.of(thumbsUpFromAlice, heartFromBob));

        List<ReactionSummaryDto> summary = chatService.getReactionSummary(message);

        assertEquals(2, summary.size());
        // Отсортировано по эмодзи - ❤️ (U+2764) идёт раньше 👍 (U+1F44D)
        // Sorted by emoji - ❤️ (U+2764) sorts before 👍 (U+1F44D)
        assertEquals("❤️", summary.get(0).getEmoji());
        assertEquals(1, summary.get(0).getCount());
        assertEquals(List.of("bob"), summary.get(0).getUsernames());
        assertEquals("👍", summary.get(1).getEmoji());
        assertEquals(List.of("alice"), summary.get(1).getUsernames());
    }
}
