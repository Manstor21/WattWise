package com.wattwise.service;

import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.AuthResponse;
import com.wattwise.model.dto.LoginRequest;
import com.wattwise.model.dto.RegisterRequest;
import com.wattwise.model.dto.UserDto;
import com.wattwise.model.entity.User;
import com.wattwise.repository.UserRepository;
import com.wattwise.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("password123");
        return req;
    }

    @Test
    void registerEncodesPasswordAndReturnsJwt() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(jwtTokenProvider.generateToken("alice")).thenReturn("jwt-token");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        AuthResponse response = userService.register(registerRequest());

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(42L);
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
        assertThat(response.getUser().getEmail()).isEqualTo("alice@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Username");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void loginSucceedsWithValidCredentials() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("$2a$10$hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$10$hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken("alice")).thenReturn("jwt-token");

        LoginRequest login = new LoginRequest();
        login.setUsername("alice");
        login.setPassword("password123");

        AuthResponse response = userService.login(login);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(7L);
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User();
        user.setUsername("alice");
        user.setPassword("$2a$10$hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        LoginRequest login = new LoginRequest();
        login.setUsername("alice");
        login.setPassword("wrong");

        assertThatThrownBy(() -> userService.login(login))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void loginSupportsEmailAsIdentifier() {
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPassword("hash");
        when(userRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken("alice")).thenReturn("token");

        LoginRequest login = new LoginRequest();
        login.setUsername("alice@example.com");
        login.setPassword("pw");

        AuthResponse response = userService.login(login);
        assertThat(response.getUser().getUsername()).isEqualTo("alice");
    }

    @Test
    void getByIdReturnsDto() {
        User user = new User();
        user.setId(3L);
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        UserDto dto = userService.getById(3L);

        assertThat(dto.getId()).isEqualTo(3L);
        assertThat(dto.getUsername()).isEqualTo("bob");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}