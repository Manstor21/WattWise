package com.wattwise.model.dto;

import com.wattwise.model.enums.ApplianceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de petición/respuesta para electrodomésticos. Cuando {@code avgCycleKwh} /
 * {@code estimatedCycleMinutes} / {@code powerWatts} se omiten al crear, el service
 * los rellena desde el catálogo embebido (ver valores por defecto de ApplianceType).
 */
public class ApplianceDto {

    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotNull(message = "Type is required")
    private ApplianceType type;

    @Min(value = 1, message = "powerWatts must be positive")
    private Integer powerWatts;

    @DecimalMin(value = "0.0", message = "avgCycleKwh must be non-negative")
    private BigDecimal avgCycleKwh;

    @Min(value = 1, message = "estimatedCycleMinutes must be positive")
    private Integer estimatedCycleMinutes;

    private Boolean isActive;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ApplianceType getType() {
        return type;
    }

    public void setType(ApplianceType type) {
        this.type = type;
    }

    public Integer getPowerWatts() {
        return powerWatts;
    }

    public void setPowerWatts(Integer powerWatts) {
        this.powerWatts = powerWatts;
    }

    public BigDecimal getAvgCycleKwh() {
        return avgCycleKwh;
    }

    public void setAvgCycleKwh(BigDecimal avgCycleKwh) {
        this.avgCycleKwh = avgCycleKwh;
    }

    public Integer getEstimatedCycleMinutes() {
        return estimatedCycleMinutes;
    }

    public void setEstimatedCycleMinutes(Integer estimatedCycleMinutes) {
        this.estimatedCycleMinutes = estimatedCycleMinutes;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}