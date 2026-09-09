package com.wattwise.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Preferencias de alerta del usuario. {@code applianceId} null = todos los electrodomésticos.
 */
public class AlertPreferenceDto {

    private Long id;

    /** Opcional: si se indica, la alerta se limita a un único electrodoméstico. */
    private Long applianceId;

    /** La alerta se dispara cuando el mejor precio disponible está este % por debajo de la media del día. */
    @DecimalMin(value = "0.0", message = "thresholdPctBelowMean must be >= 0")
    @DecimalMax(value = "100.0", message = "thresholdPctBelowMean must be <= 100")
    private BigDecimal thresholdPctBelowMean;

    private Boolean isActive;

    private LocalDateTime notifiedAt;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplianceId() {
        return applianceId;
    }

    public void setApplianceId(Long applianceId) {
        this.applianceId = applianceId;
    }

    public BigDecimal getThresholdPctBelowMean() {
        return thresholdPctBelowMean;
    }

    public void setThresholdPctBelowMean(BigDecimal thresholdPctBelowMean) {
        this.thresholdPctBelowMean = thresholdPctBelowMean;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}