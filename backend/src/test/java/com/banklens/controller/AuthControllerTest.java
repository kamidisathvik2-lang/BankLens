package com.banklens.controller;

import com.banklens.dto.AuthResponse;
import com.banklens.dto.LoginRequest;
import com.banklens.dto.RegisterRequest;
import com.banklens.exception.BankLensExceptions;
import com.banklens.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;
    @MockBean com.banklens.security.JwtUtil jwtUtil;
    @MockBean com.banklens.security.JwtAuthenticationFilter jwtFilter;
    @MockBean com.banklens.repository.UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/register returns 201 with token")
    void register_returns201() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("sathvik@example.com")
                .password("SecurePass123!")
                .fullName("Sathvik Kamidi")
                .build();

        AuthResponse resp = AuthResponse.builder()
                .token("jwt-token")
                .email("sathvik@example.com")
                .fullName("Sathvik Kamidi")
                .build();

        when(authService.register(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("sathvik@example.com"))
                .andExpect(jsonPath("$.fullName").value("Sathvik Kamidi"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for invalid email")
    void register_invalidEmail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("not-an-email")
                .password("SecurePass123!")
                .fullName("Sathvik")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 for short password")
    void register_shortPassword() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("sathvik@example.com")
                .password("short")
                .fullName("Sathvik")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 for duplicate email")
    void register_duplicateEmail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("existing@example.com")
                .password("SecurePass123!")
                .fullName("Sathvik")
                .build();

        when(authService.register(any()))
                .thenThrow(new BankLensExceptions.EmailAlreadyExistsException("existing@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with token")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest("sathvik@example.com", "SecurePass123!");
        AuthResponse resp = AuthResponse.builder()
                .token("jwt-token")
                .email("sathvik@example.com")
                .fullName("Sathvik Kamidi")
                .build();

        when(authService.login(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 when body is empty")
    void login_emptyBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
