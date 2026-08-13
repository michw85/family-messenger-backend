package com.familymessenger.backend.service;

import com.familymessenger.backend.entity.User;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final String BUCKET = "bonds-media";
    private static final String MEDIA_URL_PREFIX = "https://bonds-app.duckdns.org/media/";

    @Mock
    private MinioClient minioClient;

    private FileService fileService;

    private User uploader;

    @BeforeEach
    void setUp() {
        fileService = new FileService(minioClient);
        ReflectionTestUtils.setField(fileService, "bucketName", BUCKET);
        ReflectionTestUtils.setField(fileService, "minioUrl", "https://minio.internal:9000");

        uploader = new User();
        uploader.setId(7L);
    }

    // --- getContentType -----------------------------------------------------

    @Test
    void getContentType_recognizesJpeg() {
        assertEquals("image/jpeg", fileService.getContentType("photo.jpg"));
        assertEquals("image/jpeg", fileService.getContentType("photo.jpeg"));
    }

    @Test
    void getContentType_recognizesPng() {
        assertEquals("image/png", fileService.getContentType("photo.png"));
    }

    @Test
    void getContentType_recognizesMp3AndWebm() {
        assertEquals("audio/mpeg", fileService.getContentType("voice.mp3"));
        assertEquals("audio/mpeg", fileService.getContentType("voice.webm"));
    }

    @Test
    void getContentType_recognizesOgg() {
        assertEquals("audio/ogg", fileService.getContentType("voice.ogg"));
    }

    @Test
    void getContentType_fallsBackToOctetStreamForUnknownExtensions() {
        assertEquals("application/octet-stream", fileService.getContentType("document.pdf"));
    }

    // --- deleteFile -----------------------------------------------------

    @Test
    void deleteFile_rejectsAUrlThatDoesNotMatchTheMediaPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> fileService.deleteFile("https://evil.example.com/media/file.jpg"));
    }

    @Test
    void deleteFile_rejectsANullUrl() {
        assertThrows(IllegalArgumentException.class, () -> fileService.deleteFile(null));
    }

    @Test
    void deleteFile_removesTheObjectKeyDerivedFromTheUrl() throws Exception {
        fileService.deleteFile(MEDIA_URL_PREFIX + "videos/7_20260101_120000_abcd1234.mp4");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals(BUCKET, captor.getValue().bucket());
        assertEquals("videos/7_20260101_120000_abcd1234.mp4", captor.getValue().object());
    }

    // --- uploadFile -----------------------------------------------------

    @Test
    void uploadFile_createsTheBucketWhenItDoesNotExistYet() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        fileService.uploadFile(file, "files", uploader);

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void uploadFile_doesNotRecreateAnExistingBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        fileService.uploadFile(file, "files", uploader);

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void uploadFile_returnsAUrlUnderTheMediaPrefixWithTheGivenFolderAndExtension() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());

        String url = fileService.uploadFile(file, "files", uploader);

        assertTrue(url.startsWith(MEDIA_URL_PREFIX + "files/"));
        assertTrue(url.endsWith(".txt"));
        assertTrue(url.contains("7_"));
    }

    @Test
    void uploadFile_storesTheOriginalBytesForANonImageFolder() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        byte[] content = "hello world".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", content);

        fileService.uploadFile(file, "files", uploader);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals(content.length, captor.getValue().objectSize());
        assertEquals("text/plain", captor.getValue().contentType());
    }

    @Test
    void uploadFile_storesUndecodableBytesAsIsEvenInAnImageFolder() throws Exception {
        // Не настоящее изображение (просто текстовые байты с image/* content-type) -
        // ImageIO не сможет его декодировать, значит исходные байты должны сохраниться как есть
        // Not a real image (just text bytes with an image/* content-type) -
        // ImageIO can't decode it, so the original bytes should be stored as-is
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        byte[] content = "not actually a jpeg".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

        fileService.uploadFile(file, "images", uploader);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals(content.length, captor.getValue().objectSize());
    }
}
