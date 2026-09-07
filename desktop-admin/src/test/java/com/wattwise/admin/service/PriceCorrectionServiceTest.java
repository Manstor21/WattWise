package com.wattwise.admin.service;

import com.wattwise.admin.model.PriceRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceCorrectionServiceTest {

    private static PriceRecord record(BigDecimal price, BigDecimal tax, BigDecimal total, String color) {
        return new PriceRecord(1L, OffsetDateTime.parse("2025-06-16T08:00:00Z"),
                price, tax, total, "ESIOS", color);
    }

    @Test
    void acceptsValidRecord() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), new BigDecimal("0.021000"),
                new BigDecimal("0.121000"), "GREEN"));
        assertTrue(result.ok());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void acceptsNullTaxAndNullColor() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), null, new BigDecimal("0.100000"), null));
        assertTrue(result.ok());
    }

    @Test
    void rejectsNegativePrice() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("-0.05"), new BigDecimal("0.021000"),
                new BigDecimal("0.100000"), null));
        assertTrue(!result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("negativo")));
    }

    @Test
    void rejectsNullPrice() {
        var result = PriceCorrectionService.validate(record(null, null, new BigDecimal("0.1"), null));
        assertTrue(!result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("vacío")));
    }

    @Test
    void rejectsNegativeTotal() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), new BigDecimal("0.021000"),
                new BigDecimal("-0.10"), null));
        assertTrue(!result.ok());
    }

    @Test
    void rejectsInvalidColor() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), new BigDecimal("0.021000"),
                new BigDecimal("0.121000"), "PURPLE"));
        assertTrue(!result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Color no válido")));
    }

    @Test
    void warnsButDoesNotBlockTotalMismatch() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), new BigDecimal("0.021000"),
                new BigDecimal("0.500000"), "GREEN"));
        assertTrue(result.ok(), "mismatch is informational only");
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("no cuadra"));
    }

    @Test
    void noWarningWhenTotalMatchesWithinTolerance() {
        var result = PriceCorrectionService.validate(record(
                new BigDecimal("0.100000"), new BigDecimal("0.021000"),
                new BigDecimal("0.121000"), "AMBER"));
        assertTrue(result.warnings().isEmpty());
    }
}