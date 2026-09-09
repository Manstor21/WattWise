package com.wattwise.android.data.remote.dto;

import java.math.BigDecimal;

/**
 * DTO Gson para respuestas {@code GET /api/prices/*} (y entradas PriceDto
 * anidadas dentro de un {@link RecommendationDto}).
 *
 * <p>Los nombres de campo deben coincidir exactamente con el backend (nombres
 * tipo snake aparte de las unidades abreviadas, p. ej. {@code totalEurPerKwh}).
 * El backend serializa {@code timestamp} como ISO-8601 SIN sufijo de zona (el
 * valor almacenado es UTC); ver {@link com.wattwise.android.util.PriceUtils}
 * para la estrategia de conversión a hora local española.
 */
public class PriceDto {

    private Long id;
    private java.time.LocalDateTime timestamp;
    private BigDecimal priceEurPerKwh;
    private BigDecimal plusTaxEurPerKwh;
    private BigDecimal totalEurPerKwh;
    private String source; // Origen: ESIOS | MANUAL
    private String color; // Color: GREEN | AMBER | RED

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