package com.wattwise.model.entity;

import com.wattwise.model.enums.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single price time slot (typically 15 minutes in the Spanish PVPC market,
 * 96 records/day). The table is global (not user-scoped): it represents the
 * day-ahead electricity price published by REE / ESIOS.
 */
@Entity
@Table(
    name = "price_records",
    uniqueConstraints = @UniqueConstraint(name = "uq_price_records_timestamp", columnNames = "timestamp"),
    indexes = @Index(name = "ix_price_records_date", columnList = "date")
)
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Slot start time, UTC. Unique. */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** Net price (before taxes/peajes), EUR per kWh. */
    @Column(name = "price_eur_per_kwh", nullable = false, precision = 12, scale = 6)
    private BigDecimal priceEurPerKwh;

    /** Taxes and peajes (tolls/surcharges), EUR per kWh. May be null. */
    @Column(name = "plus_tax_eur_per_kwh", nullable = true, precision = 12, scale = 6)
    private BigDecimal plusTaxEurPerKwh;

    /** Total price (net + tax), EUR per kWh. This is what end users pay. */
    @Column(name = "total_eur_per_kwh", nullable = false, precision = 12, scale = 6)
    private BigDecimal totalEurPerKwh;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceSource source = PriceSource.ESIOS;

    /** Derived LocalDate (UTC) for range queries. */
    @Column(nullable = false)
    private LocalDate date;

    public Long getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        this.date = timestamp != null ? timestamp.toLocalDate() : null;
    }

    public BigDecimal getPriceEurPerKwh() {
        return priceEurPerKwh;
    }

    public void setPriceEurPerKwh(BigDecimal priceEurPerKwh) {
        this.priceEurPerKwh = priceEurPerKwh;
    }

    public BigDecimal getPlusTaxEurPerKwh() {
        return plusTaxEurPerKwh;
    }

    public void setPlusTaxEurPerKwh(BigDecimal plusTaxEurPerKwh) {
        this.plusTaxEurPerKwh = plusTaxEurPerKwh;
    }

    public BigDecimal getTotalEurPerKwh() {
        return totalEurPerKwh;
    }

    public void setTotalEurPerKwh(BigDecimal totalEurPerKwh) {
        this.totalEurPerKwh = totalEurPerKwh;
    }

    public PriceSource getSource() {
        return source;
    }

    public void setSource(PriceSource source) {
        this.source = source;
    }

    public LocalDate getDate() {
        return date;
    }
}
