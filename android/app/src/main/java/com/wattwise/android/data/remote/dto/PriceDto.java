package com.wattwise.android.data.remote.dto;

import java.math.BigDecimal;

/**
 * Gson DTO for {@code GET /api/prices/*} responses (and PriceDto entries nested
 * inside a {@link RecommendationDto}).
 *
 * <p>Field names must match the backend exactly (snake-ish names apart from the
 * abbreviated units, e.g. {@code totalEurPerKwh}). The backend serializes
 * {@code timestamp} as ISO-8601 WITHOUT a zone suffix (the stored value is UTC);
 * see {@link com.wattwise.android.util.PriceUtils} for the conversion strategy
 * to Spanish local time.
 */
public class PriceDto {

    private Long id;
    private java.time.LocalDateTime timestamp;
    private BigDecimal priceEurPerKwh;
    private BigDecimal plusTaxEurPerKwh;
    private BigDecimal totalEurPerKwh;
    private String source; // ESIOS | MANUAL
    private String color; // GREEN | AMBER | RED

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public java.time.LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(java.time.LocalDateTime timestamp) {
        this.timestamp = timestamp;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}