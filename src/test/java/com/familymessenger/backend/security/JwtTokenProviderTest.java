package com.familymessenger.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    // HS512 требует ключ длиной минимум 64 байта (512 бит)
    // HS512 requires a key of at least 64 bytes (512 bits)
    private static final String SECRET = "this-is-a-test-secret-key-that-is-long-enough-for-hs512-signing-1234567890";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 3600_000);
    }

    @Test
    void generateToken_roundTripsBackToTheSameUsername() {
        String token = jwtTokenProvider.generateToken("someuser");

        assertEquals("someuser", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void validateToken_trueForAFreshlyGeneratedToken() {
        String token = jwtTokenProvider.generateToken("someuser");

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_falseForAMalformedToken() {
        assertFalse(jwtTokenProvider.validateToken("not-a-real-jwt"));
    }

    @Test
    void validateToken_falseForAnExpiredToken() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", -1000);

        String alreadyExpiredToken = jwtTokenProvider.generateToken("someuser");

        assertFalse(jwtTokenProvider.validateToken(alreadyExpiredToken));
    }

    @Test
    void validateToken_falseForATokenSignedWithADifferentSecret() {
        String tokenSignedElsewhere = Jwts.builder()
                .setSubject("someuser")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor("a-completely-different-secret-key-of-sufficient-length-1234567890".getBytes()))
                .compact();

        assertFalse(jwtTokenProvider.validateToken(tokenSignedElsewhere));
    }

    @Test
    void validateToken_falseForATamperedToken() {
        String token = jwtTokenProvider.generateToken("someuser");
        // Портим символ в середине подписи, а не последний - самый последний
        // символ base64url-блока иногда кодирует неиспользуемые биты, и его
        // замена может случайно не изменить декодированные байты подписи,
        // из-за чего "испорченный" токен изредка проходил проверку (флейки)
        // Corrupt a character in the middle of the signature, not the last one -
        // the very last character of a base64url block sometimes encodes unused
        // bits, and flipping it can coincidentally leave the decoded signature
        // bytes unchanged, making the "tampered" token occasionally pass
        // validation (flaky)
        int tamperIndex = token.length() - 10;
        char originalChar = token.charAt(tamperIndex);
        char replacementChar = originalChar == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, tamperIndex) + replacementChar + token.substring(tamperIndex + 1);

        assertFalse(jwtTokenProvider.validateToken(tampered));
    }

    @Test
    void validateToken_falseForAnEmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }
}
