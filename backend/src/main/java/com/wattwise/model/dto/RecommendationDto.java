package com.wattwise.model.dto;

import com.wattwise.model.enums.ApplianceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Una recomendación de programación personalizada para un electrodoméstico: la ventana
 * de slots contigua más barata dentro de las restricciones de programación del
 * electrodoméstico, más una estimación del ahorro frente al peor slot posible.
 */
public class RecommendationDto {

    private ApplianceDto appliance;
    private ApplianceType type;
    private LocalDateTime recommendedStart;
    private LocalDateTime recommendedEnd;
    private BigDecimal recommendedPriceEurPerKwh;
    private BigDecimal estimatedCostEur;
    private BigDecimal worstCaseCostEur;
    private BigDecimal estimatedSavingsEur;
    private BigDecimal savingsPercentage;
    private String semaphore; // GREEN/AMBER/RED de la ventana recomendada
    private List<PriceDto> window;

    public ApplianceDto getAppliance() {
        return appliance;
    }

    public void setAppliance(ApplianceDto appliance) {
        this.appliance = appliance;
    }

    public ApplianceType getType() {
        return type;
    }

    public void setType(ApplianceType type) {
        this.type = type;
    }

    public LocalDateTime getRecommendedStart() {
        return recommendedStart;
    }

    public void setRecommendedStart(LocalDateTime recommendedStart) {
        this.recommendedStart = recommendedStart;
    }

    public LocalDateTime getRecommendedEnd() {
        return recommendedEnd;
    }

    public void setRecommendedEnd(LocalDateTime recommendedEnd) {
        this.recommendedEnd = recommendedEnd;
    }

    public BigDecimal getRecommendedPriceEurPerKwh() {
        return recommendedPriceEurPerKwh;
    }

    public void setRecommendedPriceEurPerKwh(BigDecimal recommendedPriceEurPerKwh) {
        this.recommendedPriceEurPerKwh = recommendedPriceEurPerKwh;
    }

    public BigDecimal getEstimatedCostEur() {
        return estimatedCostEur;
    }

    public void setEstimatedCostEur(BigDecimal estimatedCostEur) {
        this.estimatedCostEur = estimatedCostEur;
    }

    public BigDecimal getWorstCaseCostEur() {
        return worstCaseCostEur;
    }

    public void setWorstCaseCostEur(BigDecimal worstCaseCostEur) {
        this.worstCaseCostEur = worstCaseCostEur;
    }

    public BigDecimal getEstimatedSavingsEur() {
        return estimatedSavingsEur;
    }

    public void setEstimatedSavingsEur(BigDecimal estimatedSavingsEur) {
        this.estimatedSavingsEur = estimatedSavingsEur;
    }

    public BigDecimal getSavingsPercentage() {
        return savingsPercentage;
    }

    public void setSavingsPercentage(BigDecimal savingsPercentage) {
        this.savingsPercentage = savingsPercentage;
    }

    public String getSemaphore() {
        return semaphore;
    }

    public void setSemaphore(String semaphore) {
        this.semaphore = semaphore;
    }

    public List<PriceDto> getWindow() {
        return window;
    }

    public void setWindow(List<PriceDto> window) {
        this.window = window;
    }
}