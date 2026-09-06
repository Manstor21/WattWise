package com.wattwise.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal liveness endpoint. Full health details are exposed by Spring Boot
 * Actuator at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Liveness check")
public class HealthController {

    @GetMapping
    @Operation(summary = "Simple liveness check")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}