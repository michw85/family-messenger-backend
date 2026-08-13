package com.familymessenger.backend.service;

import com.familymessenger.backend.dto.AuthRequest;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registerNewUser_hashesThePasswordBeforeSaving() {
        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("Passw0rd123");
        when(passwordEncoder.encode("Passw0rd123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerNewUser(request);

        assertEquals("hashed-password", saved.getPassword());
        assertNotEquals("Passw0rd123", saved.getPassword());
    }

    @Test
    void registerNewUser_isNotApprovedUntilASuperadminApproves() {
        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("Passw0rd123");
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerNewUser(request);

        assertFalse(saved.isApproved());
    }

    @Test
    void registerNewUser_copiesUsernameAndEmailAndDefaultsToOnlineStatus() {
        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("Passw0rd123");
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = userService.registerNewUser(request);

        assertEquals("alice", saved.getUsername());
        assertEquals("alice@example.com", saved.getEmail());
        assertEquals(User.UserStatus.ONLINE, saved.getStatus());
    }

    @Test
    void existsByUsername_delegatesToTheRepository() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertTrue(userService.existsByUsername("alice"));
    }

    @Test
    void existsByEmail_delegatesToTheRepository() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertTrue(userService.existsByEmail("alice@example.com"));
    }

    @Test
    void findByUsername_throwsWhenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.findByUsername("ghost"));
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.findById(99L));
    }

    @Test
    void searchUsers_returnsEmptyForAQueryShorterThanTwoCharacters() {
        List<User> result = userService.searchUsers("a", 1L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_returnsEmptyForABlankQuery() {
        List<User> result = userService.searchUsers("   ", 1L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_returnsEmptyForANullQuery() {
        List<User> result = userService.searchUsers(null, 1L);

        assertTrue(result.isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchUsers_delegatesToTheRepositoryOnceTheQueryIsLongEnough() {
        User bob = new User();
        bob.setId(2L);
        when(userRepository.searchUsers("bo", 1L)).thenReturn(List.of(bob));

        List<User> result = userService.searchUsers("bo", 1L);

        assertEquals(List.of(bob), result);
    }

    @Test
    void updateAvatar_setsTheUrlAndSaves() {
        User alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.save(alice)).thenReturn(alice);

        User updated = userService.updateAvatar(1L, "https://example.com/avatar.jpg");

        assertEquals("https://example.com/avatar.jpg", updated.getAvatarUrl());
    }

    @Test
    void updatePassword_hashesTheNewPasswordBeforeSaving() {
        User alice = new User();
        alice.setId(1L);
        alice.setPassword("old-hashed");
        when(passwordEncoder.encode("NewPassw0rd")).thenReturn("new-hashed");

        userService.updatePassword(alice, "NewPassw0rd");

        assertEquals("new-hashed", alice.getPassword());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-hashed", captor.getValue().getPassword());
    }

    @Test
    void findByEmail_delegatesToTheRepository() {
        User alice = new User();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        assertTrue(userService.findByEmail("alice@example.com").isPresent());
    }

    // --- подтверждение регистрации суперадмином --------------------------

    private User superadminFixture(Long id) {
        User superadmin = new User();
        superadmin.setId(id);
        superadmin.setSuperadmin(true);
        return superadmin;
    }

    private User regularUserFixture(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void getPendingApprovalUsers_throwsForANonSuperadminCaller() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUserFixture(1L)));

        assertThrows(RuntimeException.class, () -> userService.getPendingApprovalUsers(1L));
        verify(userRepository, never()).findPendingApproval();
    }

    @Test
    void getPendingApprovalUsers_returnsTheListForASuperadminCaller() {
        User pending = regularUserFixture(2L);
        pending.setApproved(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superadminFixture(1L)));
        when(userRepository.findPendingApproval()).thenReturn(List.of(pending));

        List<User> result = userService.getPendingApprovalUsers(1L);

        assertEquals(List.of(pending), result);
    }

    @Test
    void approveUser_throwsForANonSuperadminCaller() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUserFixture(1L)));

        assertThrows(RuntimeException.class, () -> userService.approveUser(2L, 1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void approveUser_setsApprovedTrueForASuperadminCaller() {
        User pending = regularUserFixture(2L);
        pending.setApproved(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superadminFixture(1L)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pending));

        userService.approveUser(2L, 1L);

        assertTrue(pending.isApproved());
        verify(userRepository).save(pending);
    }

    @Test
    void rejectUser_throwsForANonSuperadminCaller() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUserFixture(1L)));

        assertThrows(RuntimeException.class, () -> userService.rejectUser(2L, 1L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectUser_blacklistsRatherThanDeletingTheAccount() {
        User pending = regularUserFixture(2L);
        pending.setApproved(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superadminFixture(1L)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(pending));

        userService.rejectUser(2L, 1L);

        assertTrue(pending.isBlacklisted());
        verify(userRepository, never()).delete(any());
        verify(userRepository).save(pending);
    }
}
