package com.familymessenger.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(redisTemplate);
    }

    @Test
    void generateAndStore_producesASixDigitCodeStoredWithA15MinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        String returnedCode = otpService.generateAndStore("someuser");

        verify(valueOperations).set(eq("login:otp:someuser"), codeCaptor.capture(), eq(Duration.ofMinutes(15)));
        assertEquals(codeCaptor.getValue(), returnedCode);
        assertTrue(returnedCode.matches("\\d{6}"));
    }

    @Test
    void generateAndStore_keyIsLowercasedForCaseInsensitiveUsernames() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        otpService.generateAndStore("SomeUser");

        verify(valueOperations).set(eq("login:otp:someuser"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void verify_trueAndDeletesTheCodeOnMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:otp:someuser")).thenReturn("123456");

        assertTrue(otpService.verify("someuser", "123456"));
        verify(redisTemplate).delete("login:otp:someuser");
    }

    @Test
    void verify_trimsWhitespaceFromTheSuppliedCode() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:otp:someuser")).thenReturn("123456");

        assertTrue(otpService.verify("someuser", " 123456 "));
    }

    @Test
    void verify_falseOnMismatchAndDoesNotDeleteAnything() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:otp:someuser")).thenReturn("123456");

        assertFalse(otpService.verify("someuser", "000000"));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verify_falseWhenNoCodeWasEverStored() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login:otp:someuser")).thenReturn(null);

        assertFalse(otpService.verify("someuser", "123456"));
    }

    @Test
    void verify_falseWhenSuppliedCodeIsNull() {
        assertFalse(otpService.verify("someuser", null));
        verify(redisTemplate, never()).opsForValue();
    }
}
