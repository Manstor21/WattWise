package com.wattwise.controller;

import com.wattwise.model.dto.ApplianceDto;
import com.wattwise.service.ApplianceService;
import com.wattwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD for the authenticated user's appliances. Every operation is scoped to
 * the JWT identity — another user's appliance resolves to 404.
 */
@RestController
@RequestMapping("/api/appliances")
@Tag(name = "Appliances", description = "User appliance management (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class ApplianceController {

    private final ApplianceService applianceService;

    public ApplianceController(ApplianceService applianceService) {
        this.applianceService = applianceService;
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's appliances")
    public List<ApplianceDto> list(Authentication authentication) {
        return applianceService.getAppliancesDto(currentUserId(authentication));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one appliance")
    public ApplianceDto get(@PathVariable Long id, Authentication authentication) {
        return applianceService.getApplianceDto(id, currentUserId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an appliance (consumption defaults from catalog if omitted)")
    public ApplianceDto create(@Valid @RequestBody ApplianceDto dto, Authentication authentication) {
        return applianceService.createDto(currentUserId(authentication), dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an appliance")
    public ApplianceDto update(@PathVariable Long id, @Valid @RequestBody ApplianceDto dto,
                               Authentication authentication) {
        return applianceService.updateDto(id, currentUserId(authentication), dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an appliance")
    public void delete(@PathVariable Long id, Authentication authentication) {
        applianceService.delete(id, currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getId();
    }
}