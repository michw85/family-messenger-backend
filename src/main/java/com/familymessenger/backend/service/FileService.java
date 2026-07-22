package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.User;
import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    // Изображения длиннее/шире этого значения (px) уменьшаются перед сохранением
    private static final int MAX_IMAGE_DIMENSION = 1600;
    // Качество JPEG после пережатия (0.0-1.0)
    private static final float IMAGE_OUTPUT_QUALITY = 0.82f;

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

        // Для изображений уменьшаем разрешение и пережимаем в JPEG перед сохранением,
        // чтобы не хранить в MinIO полноразмерные фото с камеры
        // For images, downscale and re-encode as JPEG before saving,
        // so we don't store full camera-resolution photos in MinIO
        byte[] uploadBytes;
        String uploadContentType = file.getContentType();
        if ("images".equals(folder) && uploadContentType != null && uploadContentType.startsWith("image/")) {
            byte[] compressed = compressImage(file);
            if (compressed != null) {
                uploadBytes = compressed;
                uploadContentType = "image/jpeg";
                extension = ".jpg";
            } else {
                uploadBytes = file.getBytes();
            }
        } else {
            uploadBytes = file.getBytes();
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
        try (InputStream inputStream = new ByteArrayInputStream(uploadBytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, uploadBytes.length, -1)
                            .contentType(uploadContentType)
                            .build()
            );
        }

        // Формируем URL для доступа к файлу
        // Build URL for file access
//        String fileUrl = String.format("%s/%s/%s", minioUrl, bucketName, filename);
//        String fileUrl = String.format("http://165.245.213.90:9000/%s/%s", bucketName, filename);
        String fileUrl = String.format("https://bonds-app.duckdns.org/media/%s", filename);
        log.info("File uploaded successfully: {}", fileUrl);

        return fileUrl;
    }

    /**
     * Уменьшает разрешение (если оно больше MAX_IMAGE_DIMENSION по любой стороне)
     * и пережимает изображение в JPEG заданного качества.
     * Возвращает null, если файл не удалось декодировать как растровое изображение
     * (тогда исходные байты сохраняются как есть).
     * Downscales (if larger than MAX_IMAGE_DIMENSION on either side) and
     * re-encodes the image as JPEG at the configured quality.
     * Returns null if the file couldn't be decoded as a raster image
     * (the original bytes are then stored as-is).
     */
    private byte[] compressImage(MultipartFile file) throws Exception {
        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            log.warn("Could not decode image {}, storing original bytes", file.getOriginalFilename());
            return null;
        }

        int width = original.getWidth();
        int height = original.getHeight();
        int targetWidth = width;
        int targetHeight = height;
        if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
            double scale = Math.min((double) MAX_IMAGE_DIMENSION / width, (double) MAX_IMAGE_DIMENSION / height);
            targetWidth = Math.max(1, (int) Math.round(width * scale));
            targetHeight = Math.max(1, (int) Math.round(height * scale));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(original)
                .size(targetWidth, targetHeight)
                .outputFormat("jpg")
                .outputQuality(IMAGE_OUTPUT_QUALITY)
                .toOutputStream(out);

        log.info("Compressed image {}x{} -> {}x{} ({} -> {} bytes)",
                width, height, targetWidth, targetHeight, file.getSize(), out.size());

        return out.toByteArray();
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