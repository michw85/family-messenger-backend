package com.familymessenger.backend.security;

import com.familymessenger.backend.entity.RefreshToken;
import com.familymessenger.backend.entity.User;
import com.familymessenger.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 1000L * 60 * 60 * 24 * 30);

        user = new User();
        user.setId(1L);
        user.setUsername("someuser");
    }

    @Test
    void issueRefreshToken_savesAHashOfTheTokenNotTheRawValue() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        String raw = refreshTokenService.issueRefreshToken(user);

        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(user, saved.getUser());
        assertFalse(saved.isRevoked());
    }

    @Test
    void issueRefreshToken_expiryDateIsInTheFuture() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        refreshTokenService.issueRefreshToken(user);

        verify(refreshTokenRepository).save(captor.capture());
        assertTrue(captor.getValue().getExpiryDate().isAfter(LocalDateTime.now()));
    }

    @Test
    void validateAndConsume_returnsTheOwnerAndRevokesTheTokenOnSuccess() {
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setTokenHash("irrelevant-hash");
        stored.setExpiryDate(LocalDateTime.now().plusDays(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        User result = refreshTokenService.validateAndConsume("some-raw-token");

        assertEquals(user, result);
        assertTrue(stored.isRevoked());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void validateAndConsume_rejectsAnUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.validateAndConsume("nonexistent-token"));
    }

    @Test
    void validateAndConsume_rejectsAnExpiredToken() {
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.validateAndConsume("expired-token"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void validateAndConsume_rejectsAnAlreadyRevokedTokenEvenIfNotExpiredYet() {
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setExpiryDate(LocalDateTime.now().plusDays(1));
        stored.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.validateAndConsume("already-used-token"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revoke_marksTheMatchingTokenAsRevoked() {
        RefreshToken stored = new RefreshToken();
        stored.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        refreshTokenService.revoke("some-raw-token");

        assertTrue(stored.isRevoked());
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void revoke_doesNothingWhenTheTokenIsUnknown() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> refreshTokenService.revoke("nonexistent-token"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllForUser_marksEveryActiveTokenOfThatUserAsRevoked() {
        RefreshToken t1 = new RefreshToken();
        t1.setRevoked(false);
        RefreshToken t2 = new RefreshToken();
        t2.setRevoked(false);
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(List.of(t1, t2));

        refreshTokenService.revokeAllForUser(user);

        assertTrue(t1.isRevoked());
        assertTrue(t2.isRevoked());
    }
}
