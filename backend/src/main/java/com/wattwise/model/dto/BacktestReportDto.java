package com.wattwise.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Informe completo de backtesting: comparación entre el coste naïve (sin
 * optimizar) y el coste optimizado (ventana óptima del motor) sobre un histórico
 * de precios PVPC. Incluye el desglose mensual, la mezcla de electrodomésticos
 * simulada, los supuestos del cálculo y la fuente de datos utilizada.
 */
public class BacktestReportDto {

    private LocalDate periodFrom;
    private LocalDate periodTo;
    private int daysAnalyzed;
    private BigDecimal naiveCostEur;
    private BigDecimal optimizedCostEur;
    private BigDecimal savingsEur;
    private BigDecimal savingsPercentage;
    private List<String> applianceMix;
    private List<BacktestMonthlyDto> monthlyBreakdown;
    private List<String> assumptions;
    private String dataSource;

    /** Primera fecha del histórico analizado (inclusive), en UTC. */
    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public void setPeriodFrom(LocalDate periodFrom) {
        this.periodFrom = periodFrom;
    }

    /** Última fecha del histórico analizado (inclusive), en UTC. */
    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public void setPeriodTo(LocalDate periodTo) {
        this.periodTo = periodTo;
    }

    /** Número de días completos (96 slots) analizados. */
    public int getDaysAnalyzed() {
        return daysAnalyzed;
    }

    public void setDaysAnalyzed(int daysAnalyzed) {
        this.daysAnalyzed = daysAnalyzed;
    }

    /** Coste total con la política naïve (sin optimizar), en euros. */
    public BigDecimal getNaiveCostEur() {
        return naiveCostEur;
    }

    public void setNaiveCostEur(BigDecimal naiveCostEur) {
        this.naiveCostEur = naiveCostEur;
    }

    /** Coste total optimizado (ventanas óptimas), en euros. */
    public BigDecimal getOptimizedCostEur() {
        return optimizedCostEur;
    }

    public void setOptimizedCostEur(BigDecimal optimizedCostEur) {
        this.optimizedCostEur = optimizedCostEur;
    }

    /** Ahorro total (naïve - optimizado), en euros. */
    public BigDecimal getSavingsEur() {
        return savingsEur;
    }

    public void setSavingsEur(BigDecimal savingsEur) {
        this.savingsEur = savingsEur;
    }

    /** Ahorro relativo total, en porcentaje (0..100). */
    public BigDecimal getSavingsPercentage() {
        return savingsPercentage;
    }

    public void setSavingsPercentage(BigDecimal savingsPercentage) {
        this.savingsPercentage = savingsPercentage;
    }

    /** Descripción de la mezcla de electrodomésticos simulada. */
    public List<String> getApplianceMix() {
        return applianceMix;
    }

    public void setApplianceMix(List<String> applianceMix) {
        this.applianceMix = applianceMix;
    }

    /** Desglose mensual del periodo analizado, ordenado cronológicamente. */
    public List<BacktestMonthlyDto> getMonthlyBreakdown() {
        return monthlyBreakdown;
    }

    public void setMonthlyBreakdown(List<BacktestMonthlyDto> monthlyBreakdown) {
        this.monthlyBreakdown = monthlyBreakdown;
    }

    /** Supuestos documentados del cálculo (consumos, política naïve, datos, etc.). */
    public List<String> getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(List<String> assumptions) {
        this.assumptions = assumptions;
    }

    /** Descripción de la fuente de datos utilizada. */
    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
}