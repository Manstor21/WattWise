package com.wattwise.controller;

import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.service.RecommendationService;
import com.wattwise.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Recomendaciones de programación personalizadas por electrodoméstico (requiere JWT).
 */
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Optimal time windows per appliance (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @Operation(summary = "Recommendations for all (active) appliances of the user, or a single one via applianceId")
    public List<RecommendationDto> recommendations(
            @RequestParam(value = "applianceId", required = false) Long applianceId,
            Authentication authentication) {
        Long userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        if (applianceId != null) {
            Optional<RecommendationDto> rec = recommendationService.recommendForAppliance(applianceId, userId);
            return rec.map(List::of).orElseGet(List::of);
        }
        return recommendationService.recommendAll(userId);
    }
}