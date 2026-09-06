package com.wattwise.model.entity;

import com.wattwise.model.enums.ApplianceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "appliances")
public class Appliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ApplianceType type;

    @Column(name = "power_watts", nullable = false)
    private Integer powerWatts;

    @Column(name = "avg_cycle_kwh", nullable = false, precision = 10, scale = 3)
    private BigDecimal avgCycleKwh;

    @Column(name = "estimated_cycle_minutes", nullable = false)
    private Integer estimatedCycleMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
