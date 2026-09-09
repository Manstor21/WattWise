package com.wattwise.admin.service;

import com.wattwise.admin.model.PriceRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvServiceTest {

    private static final String VALID_CSV = """
            timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
            2025-06-16T08:00:00Z,0.098200,0.021000,0.119200,ESIOS,GREEN
            2025-06-16T09:00:00Z,0.145000,0.021000,0.166000,MANUAL,
            """;

    @Test
    void parsesValidRows() throws IOException {
        var result = CsvService.parse(new StringReader(VALID_CSV));

        assertTrue(result.fileErrors().isEmpty(), "no file errors, got " + result.fileErrors());
        assertEquals(2, result.rows().size());
        assertTrue(result.rows().stream().allMatch(CsvService.CsvRow::isValid));

        PriceRecord first = result.rows().get(0).record();
        assertEquals(OffsetDateTime.parse("2025-06-16T08:00:00Z"), first.getTimestamp());
        assertEquals(new BigDecimal("0.098200"), first.getPrice());
        assertEquals(new BigDecimal("0.021000"), first.getPlusTax());
        assertEquals(new BigDecimal("0.119200"), first.getTotal());
        assertEquals("ESIOS", first.getSource());
        assertEquals("GREEN", first.getColor());
        assertEquals(java.time.LocalDate.parse("2025-06-16"), first.getDate());

        PriceRecord second = result.rows().get(1).record();
        assertEquals("MANUAL", second.getSource());
        assertEquals(null, second.getColor(), "empty color means no override");
    }

    @Test
    void rejectsWrongColumnOrder() throws IOException {
        String csv = """
                timestamp,source,total_eur_per_kwh,price_eur_per_kwh,plus_tax_eur_per_kwh,color
                2025-06-16T08:00:00Z,ESIOS,0.119200,0.098200,0.021000,GREEN
                """;
        var result = CsvService.parse(new StringReader(csv));

        assertEquals(1, result.fileErrors().size());
        assertTrue(result.fileErrors().get(0).contains("Cabecera no válida"));
        assertTrue(result.rows().isEmpty());
    }

    @Test
    void rejectsHeaderWithUnknownColumn() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,nonsense
                2025-06-16T08:00:00Z,0.098200,0.021000,0.119200,ESIOS,GREEN
                """;
        var result = CsvService.parse(new StringReader(csv));
        assertTrue(result.fileErrors().size() >= 1);
    }

    @Test
    void rejectsNegativePrice() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
                2025-06-16T08:00:00Z,-0.5,0.021,0.1,ESIOS,GREEN
                """;
        var result = CsvService.parse(new StringReader(csv));
        CsvService.CsvRow row = result.rows().get(0);
        assertTrue(!row.isValid());
        assertTrue(row.errors().stream().anyMatch(e -> e.contains("no puede ser negativo")));
        assertEquals(null, row.record());
    }

    @Test
    void rejectsBadIsoTimestamp() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
                no-es-una-fecha,0.098200,0.021000,0.119200,ESIOS,GREEN
                """;
        var result = CsvService.parse(new StringReader(csv));
        assertTrue(!result.rows().get(0).isValid());
        assertTrue(result.rows().get(0).errors().stream().anyMatch(e -> e.contains("ISO-8601")));
    }

    @Test
    void rejectsBadSourceAndBadColor() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
                2025-06-16T08:00:00Z,0.098200,0.021000,0.119200,BLUE,PURPLE
                """;
        var result = CsvService.parse(new StringReader(csv));
        CsvService.CsvRow row = result.rows().get(0);
        assertEquals(2, row.errors().size());
        assertTrue(row.errors().stream().anyMatch(e -> e.contains("ESIOS o MANUAL")));
        assertTrue(row.errors().stream().anyMatch(e -> e.contains("GREEN, AMBER, RED")));
    }

    @Test
    void warnsWhenTotalDoesNotMatchButStillValid() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
                2025-06-16T08:00:00Z,0.100000,0.021000,0.500000,ESIOS,GREEN
                """;
        var result = CsvService.parse(new StringReader(csv));
        CsvService.CsvRow row = result.rows().get(0);
        assertTrue(row.isValid(), "mismatch is only a warning");
        assertEquals(1, row.warnings().size());
        assertTrue(row.warnings().get(0).contains("no coincide"));
    }

    @Test
    void acceptsCommaAsDecimalSeparator() throws IOException {
        String csv = """
                timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
                2025-06-16T08:00:00Z,"0,098200","0,021000","0,119200",ESIOS,GREEN
                """;
        // Excel en localidad de español entrecomilla los números con coma como separador decimal.
        var result = CsvService.parse(new StringReader(csv));
        CsvService.CsvRow row = result.rows().get(0);
        assertTrue(row.isValid(), "comma decimals must be accepted: " + row.errors());
        assertEquals(new BigDecimal("0.098200"), row.record().getPrice());
        assertEquals(new BigDecimal("0.119200"), row.record().getTotal());
        assertEquals("GREEN", row.record().getColor());
    }

    @Test
    void serializeRoundTripPreservesValues() throws IOException {
        PriceRecord r1 = new PriceRecord(1,
                OffsetDateTime.parse("2025-06-16T08:00:00Z"),
                new BigDecimal("0.098200"), new BigDecimal("0.021000"),
                new BigDecimal("0.119200"), "ESIOS", "GREEN");
        PriceRecord r2 = new PriceRecord(2,
                OffsetDateTime.parse("2025-06-16T09:00:00Z"),
                new BigDecimal("0.145000"), null,
                new BigDecimal("0.145000"), "MANUAL", null);

        String csv = CsvService.serialize(List.of(r1, r2));
        var result = CsvService.parse(new StringReader(csv));

        assertTrue(result.fileErrors().isEmpty(), "round-trip header must be accepted");
        assertEquals(2, result.valid().size());

        PriceRecord parsed1 = result.rows().get(0).record();
        assertEquals(r1.getTimestamp(), parsed1.getTimestamp());
        assertEquals(0, r1.getPrice().compareTo(parsed1.getPrice()));
        assertEquals(0, r1.getPlusTax().compareTo(parsed1.getPlusTax()));
        assertEquals(0, r1.getTotal().compareTo(parsed1.getTotal()));
        assertEquals("ESIOS", parsed1.getSource());
        assertEquals("GREEN", parsed1.getColor());

        PriceRecord parsed2 = result.rows().get(1).record();
        assertEquals(null, parsed2.getPlusTax());
        assertEquals(null, parsed2.getColor());
    }

    @Test
    void stripsUtf8BomBeforeParsing() throws IOException {
        String csv = "\uFEFF" + VALID_CSV;
        var result = CsvService.parse(new StringReader(csv));
        assertTrue(result.fileErrors().isEmpty(), "BOM must be stripped: " + result.fileErrors());
        assertEquals(2, result.rows().size());
    }

    @Test
    void bundledTemplateIsImportable() throws IOException {
        try (var in = CsvServiceTest.class.getResourceAsStream("/csv-templates/price-import-template.csv")) {
            assertTrue(in != null, "template must be packaged as a resource");
            var result = CsvService.parse(in);
            assertTrue(result.fileErrors().isEmpty(),
                    "template header must be accepted: " + result.fileErrors());
            assertEquals(3, result.valid().size(),
                    "template ships 3 sample rows, all valid");
        }
    }
}