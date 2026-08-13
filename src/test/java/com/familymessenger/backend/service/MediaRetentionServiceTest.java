package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaRetentionServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private FileService fileService;

    private MediaRetentionService mediaRetentionService;

    @BeforeEach
    void setUp() {
        mediaRetentionService = new MediaRetentionService(messageRepository, fileService);
    }

    @Test
    void expireOldMedia_doesNothingWhenNothingHasExpired() throws Exception {
        when(messageRepository.findExpiredMedia(any())).thenReturn(List.of());

        mediaRetentionService.expireOldMedia();

        verifyNoInteractions(fileService);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void expireOldMedia_deletesTheFileAndMarksTheMessageOnSuccess() throws Exception {
        Message expired = new Message();
        expired.setId("msg-1");
        expired.setMediaUrl("https://bonds-app.duckdns.org/media/videos/old.mp4");
        expired.setMediaDeletedFromStorage(false);
        when(messageRepository.findExpiredMedia(any())).thenReturn(List.of(expired));

        mediaRetentionService.expireOldMedia();

        verify(fileService).deleteFile("https://bonds-app.duckdns.org/media/videos/old.mp4");
        assertTrue(expired.isMediaDeletedFromStorage());
        verify(messageRepository).save(expired);
    }

    @Test
    void expireOldMedia_leavesTheMessageUnmarkedWhenDeletionFailsSoItIsRetriedNextRun() throws Exception {
        Message expired = new Message();
        expired.setId("msg-1");
        expired.setMediaUrl("https://bonds-app.duckdns.org/media/videos/old.mp4");
        expired.setMediaDeletedFromStorage(false);
        when(messageRepository.findExpiredMedia(any())).thenReturn(List.of(expired));
        doThrow(new RuntimeException("MinIO unavailable")).when(fileService).deleteFile(anyString());

        assertDoesNotThrow(() -> mediaRetentionService.expireOldMedia());

        assertFalse(expired.isMediaDeletedFromStorage());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void expireOldMedia_continuesWithRemainingMessagesAfterOneFails() throws Exception {
        Message failing = new Message();
        failing.setId("msg-1");
        failing.setMediaUrl("https://bonds-app.duckdns.org/media/videos/bad.mp4");
        Message succeeding = new Message();
        succeeding.setId("msg-2");
        succeeding.setMediaUrl("https://bonds-app.duckdns.org/media/videos/good.mp4");
        when(messageRepository.findExpiredMedia(any())).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("MinIO unavailable")).when(fileService).deleteFile("https://bonds-app.duckdns.org/media/videos/bad.mp4");

        mediaRetentionService.expireOldMedia();

        assertFalse(failing.isMediaDeletedFromStorage());
        assertTrue(succeeding.isMediaDeletedFromStorage());
        verify(messageRepository).save(succeeding);
        verify(messageRepository, never()).save(failing);
    }
}
