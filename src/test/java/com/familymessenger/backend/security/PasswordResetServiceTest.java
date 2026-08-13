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
class PasswordResetServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(redisTemplate);
    }

    @Test
    void generateAndStore_producesASixDigitCodeStoredWithA15MinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        String returnedCode = passwordResetService.generateAndStore("someone@example.com");

        verify(valueOperations).set(eq("password:reset:someone@example.com"), codeCaptor.capture(), eq(Duration.ofMinutes(15)));
        assertEquals(codeCaptor.getValue(), returnedCode);
        assertTrue(returnedCode.matches("\\d{6}"));
    }

    @Test
    void generateAndStore_keyIsLowercasedForCaseInsensitiveEmails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        passwordResetService.generateAndStore("Someone@Example.com");

        verify(valueOperations).set(eq("password:reset:someone@example.com"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void verify_trueAndDeletesTheCodeOnMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password:reset:someone@example.com")).thenReturn("654321");

        assertTrue(passwordResetService.verify("someone@example.com", "654321"));
        verify(redisTemplate).delete("password:reset:someone@example.com");
    }

    @Test
    void verify_falseOnMismatchAndDoesNotDeleteAnything() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password:reset:someone@example.com")).thenReturn("654321");

        assertFalse(passwordResetService.verify("someone@example.com", "000000"));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verify_falseWhenNoCodeWasEverStored() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("password:reset:someone@example.com")).thenReturn(null);

        assertFalse(passwordResetService.verify("someone@example.com", "654321"));
    }

    @Test
    void verify_falseWhenSuppliedCodeIsNull() {
        assertFalse(passwordResetService.verify("someone@example.com", null));
        verify(redisTemplate, never()).opsForValue();
    }
}
