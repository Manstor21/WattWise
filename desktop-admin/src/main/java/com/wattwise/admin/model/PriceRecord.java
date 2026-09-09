package com.wattwise.admin.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Plain-Old-Java-Object que refleja una fila de la tabla {@code price_records} del
 * backend (véase backend/src/main/resources/db/migration/V1__init.sql) más el override
 * local de color de semáforo almacenado en {@code price_color_override}.
 *
 * <p>La columna {@code date} se deriva de {@code timestamp} (ambas en UTC). El campo
 * {@code color} es opcional; cuando se establece, sustituye al color derivado que usa
 * la clasificación de semáforo en el resto de WattWise.
 */
public class PriceRecord {

    public static final String SOURCE_ESIOS = "ESIOS";
    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String COLOR_GREEN = "GREEN";
    public static final String COLOR_AMBER = "AMBER";
    public static final String COLOR_RED = "RED";

    private long id;
    private OffsetDateTime timestamp;
    private BigDecimal price;
    private BigDecimal plusTax;
    private BigDecimal total;
    private String source;
    private String color;

    public PriceRecord() {
    }

    public PriceRecord(long id, OffsetDateTime timestamp, BigDecimal price,
                       BigDecimal plusTax, BigDecimal total, String source, String color) {
        this.id = id;
        this.timestamp = timestamp;
        this.price = price;
        this.plusTax = plusTax;
        this.total = total;
        this.source = source;
        this.color = color;
    }

    /** Valor de la columna {@code date} derivada (UTC), formateado para almacenamiento como {@code yyyy-MM-dd}. */
    public LocalDate getDate() {
        return timestamp == null ? null : timestamp.toLocalDate();
    }

    public String getSourceOrDefault() {
        return source == null || source.isBlank() ? SOURCE_ESIOS : source;
    }

    public boolean hasColorOverride() {
        return color != null && !color.isBlank();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPlusTax() {
        return plusTax;
    }

    public void setPlusTax(BigDecimal plusTax) {
        this.plusTax = plusTax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
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

    @Override
    public String toString() {
        return "PriceRecord{id=" + id + ", timestamp=" + timestamp + ", price=" + price
                + ", plusTax=" + plusTax + ", total=" + total + ", source=" + source
                + ", color=" + color + '}';
    }
}