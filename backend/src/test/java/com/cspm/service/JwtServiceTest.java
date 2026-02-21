package com.cspm.service;

import com.cspm.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("encoded-password")
                .email("test@example.com")
                .role("USER")
                .build();
    }

    @Test
    void generateToken_shouldReturnValidToken() {
        String token = jwtService.generateToken(testUser);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertEquals(3, token.split("\\.").length, "JWT should have 3 parts");
    }

    @Test
    void extractUsername_shouldReturnCorrectUsername() {
        String token = jwtService.generateToken(testUser);

        String username = jwtService.extractUsername(token);

        assertEquals("testuser", username);
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(testUser);

        boolean isValid = jwtService.isTokenValid(token, testUser);

        assertTrue(isValid);
    }

    @Test
    void isTokenValid_shouldReturnFalseForDifferentUser() {
        String token = jwtService.generateToken(testUser);

        User otherUser = User.builder()
                .id(2L)
                .username("otheruser")
                .password("encoded-password")
                .email("other@example.com")
                .role("USER")
                .build();

        boolean isValid = jwtService.isTokenValid(token, otherUser);

        assertFalse(isValid);
    }

    @Test
    void generateToken_differentUsersShouldGetDifferentTokens() {
        User otherUser = User.builder()
                .id(2L)
                .username("otheruser")
                .password("encoded-password")
                .email("other@example.com")
                .role("USER")
                .build();

        String token1 = jwtService.generateToken(testUser);
        String token2 = jwtService.generateToken(otherUser);

        assertNotEquals(token1, token2);
    }
}
