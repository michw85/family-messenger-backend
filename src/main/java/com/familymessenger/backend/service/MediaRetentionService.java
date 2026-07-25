package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.Message;
import com.familymessenger.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ежесуточная чистка просроченных VIDEO/FILE вложений (см. Message.mediaExpiresAt) -
 * само сообщение в истории чата остаётся, удаляется только файл в MinIO.
 * Daily cleanup of expired VIDEO/FILE attachments (see Message.mediaExpiresAt) -
 * the message itself stays in chat history, only the MinIO file is removed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaRetentionService {

    private final MessageRepository messageRepository;
    private final FileService fileService;

    // 4:30 утра - через полчаса после чистки Docker-образов по крону (4:00),
    // чтобы задачи не конкурировали за ресурсы droplet'а одновременно
    // 4:30 AM - half an hour after the Docker image prune cron (4:00), so the
    // two jobs don't compete for the droplet's resources at the same time
    @Scheduled(cron = "0 30 4 * * *")
    public void expireOldMedia() {
        List<Message> expired = messageRepository.findExpiredMedia(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        log.info("Media retention: {} attachment(s) past their expiry window", expired.size());

        for (Message message : expired) {
            try {
                fileService.deleteFile(message.getMediaUrl());
            } catch (Exception e) {
                // Не помечаем как удалённое - попробуем снова на следующем прогоне
                // Don't mark as removed - retry on the next run
                log.error("Failed to delete expired media for message {}: {}", message.getId(), e.getMessage());
                continue;
            }
            message.setMediaDeletedFromStorage(true);
            messageRepository.save(message);
        }
    }
}
