package com.wattwise.android.data.remote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO Gson para {@code GET/PUT /api/alerts/preferences}. {@code applianceId}
 * nulo significa que la preferencia aplica a todos los electrodomésticos.
 */
public class AlertPreferenceDto {

    private Long id;
    private Long applianceId;
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

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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