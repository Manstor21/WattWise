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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication and user-profile operations. Controllers have no business
 * logic — everything user/auth-related lives here.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return new AuthResponse(jwtTokenProvider.generateToken(user.getUsername()), toDto(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseGet(() -> userRepository.findByEmail(request.getUsername()).orElse(null));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ValidationException("Invalid username/email or password");
        }
        return new AuthResponse(jwtTokenProvider.generateToken(user.getUsername()), toDto(user));
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getCreatedAt());
    }
}