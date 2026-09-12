package com.wattwise.model.dto;

import java.math.BigDecimal;

/**
 * Desglose mensual del informe de backtesting: coste naïve, coste optimizado,
 * ahorro en euros y ahorro relativo para un mes concreto.
 */
public class BacktestMonthlyDto {

    private String month;
    private BigDecimal naiveCostEur;
    private BigDecimal optimizedCostEur;
    private BigDecimal savingsEur;
    private BigDecimal savingsPercentage;

    public BacktestMonthlyDto() {
    }

    public BacktestMonthlyDto(String month, BigDecimal naiveCostEur, BigDecimal optimizedCostEur,
                              BigDecimal savingsEur, BigDecimal savingsPercentage) {
        this.month = month;
        this.naiveCostEur = naiveCostEur;
        this.optimizedCostEur = optimizedCostEur;
        this.savingsEur = savingsEur;
        this.savingsPercentage = savingsPercentage;
    }

    /** Mes en formato {@code yyyy-MM}. */
    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    /** Coste de la política naïve (sin optimizar) en euros. */
    public BigDecimal getNaiveCostEur() {
        return naiveCostEur;
    }

    public void setNaiveCostEur(BigDecimal naiveCostEur) {
        this.naiveCostEur = naiveCostEur;
    }

    /** Coste optimizado (ventana óptima del motor) en euros. */
    public BigDecimal getOptimizedCostEur() {
        return optimizedCostEur;
    }

    public void setOptimizedCostEur(BigDecimal optimizedCostEur) {
        this.optimizedCostEur = optimizedCostEur;
    }

    /** Ahorro del mes (naïve - optimizado) en euros. */
    public BigDecimal getSavingsEur() {
        return savingsEur;
    }

    public void setSavingsEur(BigDecimal savingsEur) {
        this.savingsEur = savingsEur;
    }

    /** Ahorro relativo del mes en porcentaje (0..100). */
    public BigDecimal getSavingsPercentage() {
        return savingsPercentage;
    }

    public void setSavingsPercentage(BigDecimal savingsPercentage) {
        this.savingsPercentage = savingsPercentage;
    }
}