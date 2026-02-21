package com.cspm.controller;

import com.cspm.model.AuthRequest;
import com.cspm.model.RegisterRequest;
import com.cspm.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_withValidRequest_shouldReturnCreated() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "password123", "test@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void register_withDuplicateUsername_shouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "password123", "test@example.com");

        // Register first time
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Register again with same username
        RegisterRequest duplicate = new RegisterRequest("testuser", "password456", "other@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    void register_withBlankUsername_shouldReturnValidationError() throws Exception {
        RegisterRequest request = new RegisterRequest("", "password123", "test@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.username").isNotEmpty());
    }

    @Test
    void register_withShortPassword_shouldReturnValidationError() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "short", "test@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.password").isNotEmpty());
    }

    @Test
    void register_withInvalidEmail_shouldReturnValidationError() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "password123", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.email").isNotEmpty());
    }

    @Test
    void login_withValidCredentials_shouldReturnToken() throws Exception {
        // Register first
        RegisterRequest registerReq = new RegisterRequest("testuser", "password123", "test@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Login
        AuthRequest loginReq = new AuthRequest("testuser", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void login_withInvalidPassword_shouldReturnUnauthorized() throws Exception {
        // Register first
        RegisterRequest registerReq = new RegisterRequest("testuser", "password123", "test@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        // Login with wrong password
        AuthRequest loginReq = new AuthRequest("testuser", "wrongpassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void login_withBlankFields_shouldReturnValidationError() throws Exception {
        AuthRequest loginReq = new AuthRequest("", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }
}
