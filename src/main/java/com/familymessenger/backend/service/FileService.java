package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.User;
import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Сервис для работы с файлами (MinIO)
 * Service for file operations (MinIO)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.url}")
    private String minioUrl;

    /**
     * Загрузка файла в MinIO
     * Upload file to MinIO
     *
     * @param file - файл для загрузки / file to upload
     * @param folder - папка (images, voice) / folder
     * @param user - пользователь, загрузивший файл / user who uploaded
     * @return публичная ссылка на файл / public file URL
     */
    public String uploadFile(MultipartFile file, String folder, User user) throws Exception {

        log.info("Uploading file to MinIO: {}", file.getOriginalFilename());

        // Проверяем, существует ли bucket, если нет - создаём
        // Check if bucket exists, if not - create it
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket created: {}", bucketName);
        }

        // Генерируем уникальное имя файла
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String filename = String.format("%s/%s_%s_%s%s",
                folder,
                user.getId(),
                timestamp,
                uniqueId,
                extension);

        // Загружаем файл в MinIO
        // Upload file to MinIO
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }

        // Формируем URL для доступа к файлу
        // Build URL for file access
        String fileUrl = String.format("%s/%s/%s", minioUrl, bucketName, filename);

        log.info("File uploaded successfully: {}", fileUrl);

        return fileUrl;
    }

    /**
     * Скачивание файла из MinIO
     * Download file from MinIO
     *
     * @param filename - имя файла / filename
     * @return содержимое файла / file content
     */
    public byte[] downloadFile(String filename) throws Exception {

        log.info("Downloading file from MinIO: {}", filename);

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(filename)
                        .build())) {

            return inputStream.readAllBytes();
        }
    }

    /**
     * Получение content-type файла
     * Get file content-type
     */
    public String getContentType(String filename) {
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".mp3") || filename.endsWith(".webm")) {
            return "audio/mpeg";
        } else if (filename.endsWith(".ogg")) {
            return "audio/ogg";
        }
        return "application/octet-stream";
    }
}