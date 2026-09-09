package com.wattwise.android.data.remote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO Gson para respuestas {@code GET /api/recommendations}. La ventana
 * contiene las franjas {@link PriceDto} que forman el periodo recomendado, más
 * el cálculo de ahorro realizado en el servidor.
 */
public class RecommendationDto {

    private ApplianceDto appliance;
    private String type;
    private LocalDateTime recommendedStart;
    private LocalDateTime recommendedEnd;
    private BigDecimal recommendedPriceEurPerKwh;
    private BigDecimal estimatedCostEur;
    private BigDecimal worstCaseCostEur;
    private BigDecimal estimatedSavingsEur;
    private BigDecimal savingsPercentage;
    private String semaphore; // Semáforo: GREEN | AMBER | RED
    private List<PriceDto> window;

    public ApplianceDto getAppliance() {
        return appliance;
    }

    public void setAppliance(ApplianceDto appliance) {
        this.appliance = appliance;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
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