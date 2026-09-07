package com.wattwise.admin.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Plain-Old-Java-Object mirroring one row of the backend {@code price_records}
 * table (see backend/src/main/resources/db/migration/V1__init.sql) plus the local
 * traffic-light colour override stored in {@code price_color_override}.
 *
 * <p>The {@code date} column is derived from {@code timestamp} (both UTC). The
 * {@code color} field is optional; when set it overrides the derived colour used
 * by the traffic-light classification elsewhere in WattWise.
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

    /** Derived {@code date} column value (UTC), formatted for storage as {@code yyyy-MM-dd}. */
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