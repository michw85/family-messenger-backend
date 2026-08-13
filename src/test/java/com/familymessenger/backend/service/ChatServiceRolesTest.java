package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.ChatRoom;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.ChatRoomRepository;
import com.familymessenger.backend.repository.MessageReactionRepository;
import com.familymessenger.backend.repository.MessageRepository;
import com.familymessenger.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты на самую чувствительную к безопасности логику ChatService -
 * кто может удалить/скрыть чат, кому доступен блокнот-суперадмин-обход.
 * Мокаем все репозитории (@Mock) вместо реальной БД - это ЮНИТ-тест, он
 * проверяет только бизнес-логику метода в изоляции, быстро и без Spring
 * context. @InjectMocks создаёт ChatService и подставляет туда моки вместо
 * реальных бинов через конструктор (@RequiredArgsConstructor).
 * Unit tests for ChatService's most security-sensitive logic - who can
 * delete/hide a chat, who gets the notebook superadmin-override. All
 * repositories are mocked (@Mock) instead of a real DB - this is a UNIT
 * test, it only checks the method's business logic in isolation, fast and
 * without a Spring context. @InjectMocks builds ChatService and wires the
 * mocks in through its constructor (@RequiredArgsConstructor).
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceRolesTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MessageReactionRepository messageReactionRepository;

    @InjectMocks
    private ChatService chatService;

    private static final String CHAT_ID = "chat-1";
    private static final Long CREATOR_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SUPERADMIN_ID = 99L;

    private User creator;
    private User otherUser;
    private User superadmin;
    private ChatRoom groupChat;
    private ChatRoom notebookChat;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(CREATOR_ID);
        creator.setUsername("creator");

        otherUser = new User();
        otherUser.setId(OTHER_USER_ID);
        otherUser.setUsername("other");

        superadmin = new User();
        superadmin.setId(SUPERADMIN_ID);
        superadmin.setUsername("owner");
        superadmin.setSuperadmin(true);

        groupChat = new ChatRoom();
        groupChat.setId(CHAT_ID);
        groupChat.setName("Family group");
        groupChat.setType(ChatRoom.RoomType.GROUP);
        groupChat.setCreatedBy(creator);
        groupChat.getParticipants().add(creator);
        groupChat.getParticipants().add(otherUser);

        notebookChat = new ChatRoom();
        notebookChat.setId(CHAT_ID);
        notebookChat.setName("__notebook__");
        notebookChat.setType(ChatRoom.RoomType.DIRECT);
        notebookChat.setCreatedBy(creator);
        notebookChat.getParticipants().add(creator);
    }

    // --- deleteChat: обычная группа --------------------------------------

    @Test
    void deleteChat_creatorCanDeleteTheirOwnGroup() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));

        assertDoesNotThrow(() -> chatService.deleteChat(CHAT_ID, CREATOR_ID));

        verify(chatRoomRepository).delete(groupChat);
    }

    @Test
    void deleteChat_randomMemberCannotDeleteSomeoneElsesGroup() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertThrows(RuntimeException.class, () -> chatService.deleteChat(CHAT_ID, OTHER_USER_ID));

        // Раз доступа нет - реального удаления произойти не должно
        // No access means no actual deletion should happen
        verify(chatRoomRepository, never()).delete(any());
    }

    @Test
    void deleteChat_groupAdminCanDeleteEvenWithoutBeingCreator() {
        groupChat.getGroupAdminUserIds().add(OTHER_USER_ID);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertDoesNotThrow(() -> chatService.deleteChat(CHAT_ID, OTHER_USER_ID));

        verify(chatRoomRepository).delete(groupChat);
    }

    @Test
    void deleteChat_superadminCanDeleteAnyoneElsesGroup() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(SUPERADMIN_ID)).thenReturn(Optional.of(superadmin));

        assertDoesNotThrow(() -> chatService.deleteChat(CHAT_ID, SUPERADMIN_ID));

        verify(chatRoomRepository).delete(groupChat);
    }

    // --- deleteChat: блокнот - тут суть задачи #61 -----------------------

    @Test
    void deleteChat_evenTheNotebookOwnerCannotPermanentlyDeleteIt() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(notebookChat));
        when(userRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));

        assertThrows(RuntimeException.class, () -> chatService.deleteChat(CHAT_ID, CREATOR_ID));

        verify(chatRoomRepository, never()).delete(any());
    }

    @Test
    void deleteChat_superadminCanPermanentlyDeleteSomeoneElsesNotebook() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(notebookChat));
        when(userRepository.findById(SUPERADMIN_ID)).thenReturn(Optional.of(superadmin));

        assertDoesNotThrow(() -> chatService.deleteChat(CHAT_ID, SUPERADMIN_ID));

        verify(chatRoomRepository).delete(notebookChat);
    }

    // --- leaveChat: скрытие блокнота НЕ должно удалять его целиком -------

    @Test
    void leaveChat_hidingYourOwnNotebookDoesNotDeleteIt() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(notebookChat));

        chatService.leaveChat(CHAT_ID, CREATOR_ID);

        assertTrue(notebookChat.getHiddenForUserIds().contains(CREATOR_ID),
                "должен быть скрыт для владельца / should be hidden for the owner");
        verify(chatRoomRepository, never()).delete(any());
        verify(chatRoomRepository).save(notebookChat);
    }

    @Test
    void leaveChat_aRegularPersonalChatStillFullyDeletesOnceEveryoneHidIt() {
        // Обычный (не блокнот) личный чат вдвоём - оба уже скрыли его у себя
        // A regular (non-notebook) 2-person personal chat - both already hid it
        ChatRoom personalChat = new ChatRoom();
        personalChat.setId(CHAT_ID);
        personalChat.setName("Regular DM");
        personalChat.setType(ChatRoom.RoomType.DIRECT);
        personalChat.setCreatedBy(creator);
        personalChat.getParticipants().add(creator);
        personalChat.getParticipants().add(otherUser);
        personalChat.getHiddenForUserIds().add(OTHER_USER_ID);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(personalChat));

        chatService.leaveChat(CHAT_ID, CREATOR_ID);

        verify(chatRoomRepository).delete(personalChat);
    }

    // --- blacklistUser: строго только суперадмин --------------------------

    @Test
    void blacklistUser_regularUserCannotBlacklistAnyone() {
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertThrows(RuntimeException.class, () -> chatService.blacklistUser(CREATOR_ID, OTHER_USER_ID));

        verify(userRepository, never()).save(any());
    }

    @Test
    void blacklistUser_superadminCanBlacklistAUser() {
        when(userRepository.findById(SUPERADMIN_ID)).thenReturn(Optional.of(superadmin));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        chatService.blacklistUser(OTHER_USER_ID, SUPERADMIN_ID);

        assertTrue(otherUser.isBlacklisted());
        verify(userRepository).save(otherUser);
    }

    @Test
    void unblacklistUser_regularUserCannotUnblacklistAnyone() {
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertThrows(RuntimeException.class, () -> chatService.unblacklistUser(CREATOR_ID, OTHER_USER_ID));

        verify(userRepository, never()).save(any());
    }

    @Test
    void unblacklistUser_superadminCanUnblacklistAUser() {
        otherUser.setBlacklisted(true);
        when(userRepository.findById(SUPERADMIN_ID)).thenReturn(Optional.of(superadmin));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        chatService.unblacklistUser(OTHER_USER_ID, SUPERADMIN_ID);

        assertFalse(otherUser.isBlacklisted());
        verify(userRepository).save(otherUser);
    }

    // --- removeParticipant (кик из группы) -------------------------------

    @Test
    void removeParticipant_creatorCanKickAnyone() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));
        when(userRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));

        chatService.removeParticipant(CHAT_ID, OTHER_USER_ID, CREATOR_ID);

        assertFalse(groupChat.getParticipants().contains(otherUser));
        verify(chatRoomRepository).save(groupChat);
    }

    @Test
    void removeParticipant_editorCanKickSomeoneButIsNotTheCreator() {
        // Третий участник - редактор (не создатель, не админ группы) - должен
        // всё равно суметь кикнуть otherUser
        // A third participant is an editor (not creator, not group admin) -
        // should still be able to kick otherUser
        User editor = new User();
        editor.setId(3L);
        editor.setUsername("editor");
        groupChat.getParticipants().add(editor);
        groupChat.getEditorUserIds().add(3L);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(editor));

        chatService.removeParticipant(CHAT_ID, OTHER_USER_ID, 3L);

        assertFalse(groupChat.getParticipants().contains(otherUser));
        verify(chatRoomRepository).save(groupChat);
    }

    @Test
    void removeParticipant_randomMemberCannotKickSomeoneElse() {
        // otherUser (обычный участник, без ролей) пытается кикнуть создателя -
        // не должно получиться
        // otherUser (a plain member, no roles) tries to kick the creator -
        // should not be allowed
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertThrows(RuntimeException.class,
                () -> chatService.removeParticipant(CHAT_ID, CREATOR_ID, OTHER_USER_ID));

        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void removeParticipant_aParticipantCanAlwaysRemoveThemself() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        // otherUser убирает сам себя - не создатель, не админ, не редактор,
        // не суперадмин, но это разрешено, т.к. userId == currentUserId
        // otherUser removes themself - not creator, not admin, not editor,
        // not superadmin, but allowed because userId == currentUserId
        assertDoesNotThrow(() -> chatService.removeParticipant(CHAT_ID, OTHER_USER_ID, OTHER_USER_ID));

        assertFalse(groupChat.getParticipants().contains(otherUser));
    }

    // --- promoteGroupAdmin / demoteGroupAdmin - создатель или суперадмин --

    @Test
    void promoteGroupAdmin_creatorCanPromoteSomeoneToAdmin() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.promoteGroupAdmin(CHAT_ID, OTHER_USER_ID, CREATOR_ID);

        assertTrue(groupChat.getGroupAdminUserIds().contains(OTHER_USER_ID));
        verify(chatRoomRepository).save(groupChat);
    }

    @Test
    void promoteGroupAdmin_randomMemberCannotPromoteAnyone() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(OTHER_USER_ID)).thenReturn(Optional.of(otherUser));

        assertThrows(RuntimeException.class,
                () -> chatService.promoteGroupAdmin(CHAT_ID, OTHER_USER_ID, OTHER_USER_ID));

        assertFalse(groupChat.getGroupAdminUserIds().contains(OTHER_USER_ID));
    }

    @Test
    void demoteGroupAdmin_creatorCanRemoveAdminRole() {
        groupChat.getGroupAdminUserIds().add(OTHER_USER_ID);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.demoteGroupAdmin(CHAT_ID, OTHER_USER_ID, CREATOR_ID);

        assertFalse(groupChat.getGroupAdminUserIds().contains(OTHER_USER_ID));
    }

    // --- promoteEditor / demoteEditor - создатель, админ группы, суперадмин

    @Test
    void promoteEditor_groupAdminCanPromoteSomeoneToEditor() {
        // "creator" тут выступает в роли назначенного админа группы, а не
        // самого создателя - показываем, что этого достаточно
        // "creator" here acts as a promoted group admin, not the actual
        // creator - showing that this alone is enough
        groupChat.getGroupAdminUserIds().add(CREATOR_ID);
        groupChat.setCreatedBy(otherUser);
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        chatService.promoteEditor(CHAT_ID, OTHER_USER_ID, CREATOR_ID);

        assertTrue(groupChat.getEditorUserIds().contains(OTHER_USER_ID));
    }

    @Test
    void promoteEditor_randomMemberCannotPromoteAnyone() {
        when(chatRoomRepository.findById(CHAT_ID)).thenReturn(Optional.of(groupChat));

        assertThrows(RuntimeException.class,
                () -> chatService.promoteEditor(CHAT_ID, OTHER_USER_ID, OTHER_USER_ID));

        assertFalse(groupChat.getEditorUserIds().contains(OTHER_USER_ID));
    }
}
