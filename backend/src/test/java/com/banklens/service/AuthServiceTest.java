package com.banklens.service;

import com.banklens.dto.AuthResponse;
import com.banklens.dto.LoginRequest;
import com.banklens.dto.RegisterRequest;
import com.banklens.entity.User;
import com.banklens.exception.BankLensExceptions;
import com.banklens.repository.UserRepository;
import com.banklens.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthService authService;

    private RegisterRequest validRegisterRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .email("sathvik@example.com")
                .password("SecurePass123!")
                .fullName("Sathvik Kamidi")
                .build();

        savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("sathvik@example.com")
                .passwordHash("$2a$12$hashedpassword")
                .fullName("Sathvik Kamidi")
                .build();
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: creates user and returns JWT")
    void register_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken(anyString())).thenReturn("jwt-token");

        AuthResponse response = authService.register(validRegisterRequest);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("sathvik@example.com");
        assertThat(response.getFullName()).isEqualTo("Sathvik Kamidi");

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("sathvik@example.com") &&
                u.getPasswordHash().equals("$2a$12$hash")
        ));
    }

    @Test
    @DisplayName("register: throws when email already exists")
    void register_emailExists() {
        when(userRepository.existsByEmail("sathvik@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(BankLensExceptions.EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: normalizes email to lowercase")
    void register_normalizesEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("SATHVIK@EXAMPLE.COM")
                .password("Pass12345!")
                .fullName("Sathvik")
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateToken(anyString())).thenReturn("token");

        authService.register(request);

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("sathvik@example.com")));
    }

    @Test
    @DisplayName("register: password is hashed before saving")
    void register_passwordHashed() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("hashed!");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateToken(anyString())).thenReturn("token");

        authService.register(validRegisterRequest);

        verify(passwordEncoder).encode("SecurePass123!");
        verify(userRepository).save(argThat(u ->
                !u.getPasswordHash().equals("SecurePass123!")));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: returns JWT for valid credentials")
    void login_success() {
        LoginRequest request = new LoginRequest("sathvik@example.com", "SecurePass123!");

        when(userRepository.findByEmail("sathvik@example.com")).thenReturn(Optional.of(savedUser));
        when(jwtUtil.generateToken("sathvik@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("sathvik@example.com");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException on bad password")
    void login_badCredentials() {
        LoginRequest request = new LoginRequest("sathvik@example.com", "wrongpass");

        doThrow(new BadCredentialsException("bad creds"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
