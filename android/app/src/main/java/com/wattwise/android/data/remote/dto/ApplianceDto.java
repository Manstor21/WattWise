package com.wattwise.android.data.remote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Gson DTO mirroring the backend {@code ApplianceDto}. Used both for the
 * appliance REST contract and nested inside {@link RecommendationDto}.
 */
public class ApplianceDto {

    private Long id;
    private String name;
    private String type; // WASHING_MACHINE | DISHWASHER | EV_CHARGER | DRYER | POOL_PUMP | AC | OTHER
    private Integer powerWatts;
    private BigDecimal avgCycleKwh;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
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

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}