package com.wattwise.controller;

import com.wattwise.model.dto.AlertPreferenceDto;
import com.wattwise.security.UserPrincipal;
import com.wattwise.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Preferencias de alerta del usuario (requiere JWT).
 */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Alert preferences and evaluation (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get the authenticated user's alert preferences")
    public AlertPreferenceDto getPreferences(Authentication authentication) {
        return alertService.getPreferences(currentUserId(authentication));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update alert preferences")
    public AlertPreferenceDto updatePreferences(@Valid @RequestBody AlertPreferenceDto dto,
                                                Authentication authentication) {
        return alertService.updatePreferences(currentUserId(authentication), dto);
    }

    @GetMapping("/check")
    @Operation(summary = "Evaluate today's prices against the user's alert preferences (returns fired alerts)")
    public List<String> check(Authentication authentication) {
        return alertService.checkForUser(currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getId();
    }
}