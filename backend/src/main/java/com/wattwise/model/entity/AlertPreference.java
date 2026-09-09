package com.wattwise.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Configuración de alertas del usuario. Una fila por usuario. Cuando {@code applianceId}
 * es null, la preferencia se aplica a todos los electrodomésticos del usuario
 * (una alerta "global").
 */
@Entity
@Table(name = "alert_preferences")
public class AlertPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appliance_id", nullable = true)
    private Appliance appliance;

    /** La alerta se dispara cuando el mejor precio disponible está al menos este % por debajo de la media del día. */
    @Column(name = "threshold_pct_below_mean", nullable = false, precision = 6, scale = 2)
    private BigDecimal thresholdPctBelowMean = new BigDecimal("20.00");

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Appliance getAppliance() {
        return appliance;
    }

    public void setAppliance(Appliance appliance) {
        this.appliance = appliance;
    }

    public BigDecimal getThresholdPctBelowMean() {
        return thresholdPctBelowMean;
    }

    public void setThresholdPctBelowMean(BigDecimal thresholdPctBelowMean) {
        this.thresholdPctBelowMean = thresholdPctBelowMean;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
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
