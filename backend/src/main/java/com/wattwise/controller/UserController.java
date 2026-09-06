package com.wattwise.controller;

import com.wattwise.model.dto.UserDto;
import com.wattwise.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User profile queries (JWT required).
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User profile (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user profile by id")
    public UserDto getById(@PathVariable Long id) {
        return userService.getById(id);
    }
}