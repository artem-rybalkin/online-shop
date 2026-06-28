package com.shop.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("null")
@Test
    void register_ShouldSetJwtCookie_WhenValidRequest() throws Exception {
        // Given
        AuthRequest request = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .email("test@example.com")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @SuppressWarnings("null")
@Test
    void register_ShouldReturnBadRequest_WhenUsernameExists() throws Exception {
        // Given - first register a user
        AuthRequest firstRequest = AuthRequest.builder()
                .username("existinguser")
                .password("password123")
                .email("existing@example.com")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)));

        // When & Then - try to register with same username
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Username already exists")));
    }

    @SuppressWarnings("null")
@Test
    void login_ShouldSetJwtCookie_WhenValidCredentials() throws Exception {
        // Given - register user first
        AuthRequest registerRequest = AuthRequest.builder()
                .username("loginuser")
                .password("password123")
                .email("login@example.com")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        // When & Then - login with same credentials
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(cookie().httpOnly("jwt", true))
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @SuppressWarnings("null")
@Test
    void login_ShouldReturnUnauthorized_WhenInvalidCredentials() throws Exception {
        // Given
        AuthRequest request = AuthRequest.builder()
                .username("nonexistent")
                .password("wrongpassword")
                .email("test@example.com")
                .build();

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @SuppressWarnings("null")
    @Test
    void register_ShouldReturnBadRequest_WhenEmailIsInvalid() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("validuser2")
                .password("password123")
                .email("not-an-email")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please provide a valid email address"));
    }

    @SuppressWarnings("null")
    @Test
    void register_ShouldReturnBadRequest_WhenEmailIsBlank() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("validuser3")
                .password("password123")
                .email("")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @SuppressWarnings("null")
    @Test
    void register_ShouldReturnBadRequest_WhenUsernameIsBlank() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("")
                .password("password123")
                .email("blank-username@example.com")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is required"));
    }

    @SuppressWarnings("null")
    @Test
    void register_ShouldReturnBadRequest_WhenPasswordIsBlank() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("blank-password-user")
                .password("")
                .email("blank-password@example.com")
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is required"));
    }

    @SuppressWarnings("null")
    @Test
    void login_ShouldSucceed_WithNoEmailField_LikeTheRealFrontendSends() throws Exception {
        // The browser frontend (app.js) only ever sends {username, password} to /login —
        // it never includes an email field. Login must not require one.
        AuthRequest registerRequest = AuthRequest.builder()
                .username("frontend-shaped-login-user")
                .password("password123")
                .email("frontend-shaped-login-user@example.com")
                .build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"frontend-shaped-login-user\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("jwt"))
                .andExpect(jsonPath("$.username").value("frontend-shaped-login-user"));
    }

    @SuppressWarnings("null")
    @Test
    void login_ShouldReturnBadRequest_WhenUsernameIsBlank() throws Exception {
        AuthRequest request = AuthRequest.builder()
                .username("")
                .password("password123")
                .email("blank-login-username@example.com")
                .build();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username is required"));
    }

    @Test
    void me_ShouldReturnUnauthorized_WhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("null")
    @Test
    void me_ShouldReturnCurrentUser_WhenLoggedIn() throws Exception {
        AuthRequest creds = AuthRequest.builder()
                .username("me-endpoint-user")
                .password("password123")
                .email("me-endpoint-user@example.com")
                .build();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)));

        var loginResponse = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creds)))
                .andReturn().getResponse();

        mockMvc.perform(get("/api/auth/me").cookie(loginResponse.getCookie("jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me-endpoint-user"))
                .andExpect(jsonPath("$.email").value("me-endpoint-user@example.com"));
    }

    @Test
    void logout_ShouldReturnOk_AndExpireTheJwtCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out"))
                .andExpect(cookie().maxAge("jwt", 0))
                .andExpect(cookie().value("jwt", ""));
    }
}