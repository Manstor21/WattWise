package com.wattwise.model.dto;

import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de la API de un slot de precio. Incluye el color de semáforo calculado por
 * TrafficLightClassifier. Alineada con el renderizado de celdas del panel web
 * (precio en EUR/kWh + color).
 */
public class PriceDto {

    private Long id;
    private LocalDateTime timestamp;
    private BigDecimal priceEurPerKwh;
    private BigDecimal plusTaxEurPerKwh;
    private BigDecimal totalEurPerKwh;
    private PriceSource source;
    private TrafficLight color;

    public PriceDto() {
    }

    public PriceDto(Long id, LocalDateTime timestamp, BigDecimal priceEurPerKwh,
                    BigDecimal plusTaxEurPerKwh, BigDecimal totalEurPerKwh,
                    PriceSource source, TrafficLight color) {
        this.id = id;
        this.timestamp = timestamp;
        this.priceEurPerKwh = priceEurPerKwh;
        this.plusTaxEurPerKwh = plusTaxEurPerKwh;
        this.totalEurPerKwh = totalEurPerKwh;
        this.source = source;
        this.color = color;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
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

    public PriceSource getSource() {
        return source;
    }

    public void setSource(PriceSource source) {
        this.source = source;
    }

    public TrafficLight getColor() {
        return color;
    }

    public void setColor(TrafficLight color) {
        this.color = color;
    }
}