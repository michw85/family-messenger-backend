package com.familymessenger.backend.controller;

import com.familymessenger.backend.service.ChatService;
import com.familymessenger.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.familymessenger.backend.entity.User;

import java.util.Map;

/**
 * Контроллер для загрузки и скачивания файлов (фото, голосовые сообщения)
 * Controller for file upload and download (images, voice messages)
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final ChatService chatService;

    /**
     * Загрузка изображения
     * Upload image
     *
     * @param file - файл изображения / image file
     * @param user - текущий пользователь / current user
     * @return ссылка на загруженный файл / uploaded file URL
     */
    @PostMapping("/upload/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal User user) {

        log.info("Uploading image: {} by user: {}", file.getOriginalFilename(), user.getUsername());

        try {
            // Валидация файла
            // File validation
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty / Файл пуст");
            }

            // Проверка типа файла (только изображения)
            // Check file type (images only)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body("Only image files are allowed / Разрешены только изображения");
            }

            // Проверка размера (максимум 10 МБ)
            // Check file size (max 10 MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size exceeds 10MB / Размер файла превышает 10 МБ");
            }

            // Загружаем файл
            // Upload file
            String fileUrl = fileService.uploadFile(file, "images", user);

            return ResponseEntity.ok(Map.of(
                    "url", fileUrl,
                    "type", "image",
                    "message", "File uploaded successfully / Файл успешно загружен"
            ));

        } catch (Exception e) {
            log.error("Failed to upload image: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload file / Ошибка загрузки файла");
        }
    }

    /**
     * Загрузка голосового сообщения
     * Upload voice message
     *
     * @param file - аудио файл / audio file
     * @param user - текущий пользователь / current user
     * @return ссылка на загруженный файл / uploaded file URL
     */
    @PostMapping("/upload/voice")
    public ResponseEntity<?> uploadVoice(@RequestParam("file") MultipartFile file,
                                         @AuthenticationPrincipal User user) {

        log.info("Uploading voice message: {} by user: {}", file.getOriginalFilename(), user.getUsername());

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("File is empty / Файл пуст");
            }

            // Проверка типа файла (аудио)
            // Check file type (audio only)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("audio/")) {
                return ResponseEntity.badRequest().body("Only audio files are allowed / Разрешены только аудиофайлы");
            }

            // Проверка размера (максимум 5 МБ для голосовых)
            // Check file size (max 5 MB for voice)
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("Voice message size exceeds 5MB / Размер голосового сообщения превышает 5 МБ");
            }

            // Загружаем файл
            // Upload file
            String fileUrl = fileService.uploadFile(file, "voice", user);

            return ResponseEntity.ok(Map.of(
                    "url", fileUrl,
                    "type", "voice",
                    "message", "Voice message uploaded successfully / Голосовое сообщение успешно загружено"
            ));

        } catch (Exception e) {
            log.error("Failed to upload voice message: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload voice message / Ошибка загрузки голосового сообщения");
        }
    }

    /**
     * Получение файла (для доступа к загруженным файлам).
     * Доступ разрешён только участникам чата, к которому относится файл.
     * Get file (for accessing uploaded files).
     * Access is only allowed to participants of the chat the file belongs to.
     *
     * @param filename - имя файла (может включать папку, напр. images/xxx.jpg) / filename (may include folder)
     * @param user - текущий авторизованный пользователь / current authenticated user
     * @return файл / file
     */
    @GetMapping("/download/{*filename}")
    public ResponseEntity<?> downloadFile(@PathVariable String filename,
                                          @AuthenticationPrincipal User user) {

        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!chatService.canAccessMediaFile(filename, user.getId())) {
            log.warn("User {} denied access to file: {}", user.getUsername(), filename);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have access to this file / Нет доступа к этому файлу");
        }

        log.info("Downloading file: {} by user: {}", filename, user.getUsername());

        try {
            byte[] fileData = fileService.downloadFile(filename);
            String contentType = fileService.getContentType(filename);

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .body(fileData);

        } catch (Exception e) {
            log.error("Failed to download file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found / Файл не найден");
        }
    }
}