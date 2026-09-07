package com.wattwise.admin.service;

import com.wattwise.admin.model.PriceRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * CSV parsing and serialization (RFC 4180) with strict validation for the WattWise price import.
 *
 * <p>Column layout (kept as the single contract between template, parser and exporter — the
 * schema's {@code id} and derived {@code date} columns are intentionally not part of the file):
 *
 * <pre>
 * timestamp, price_eur_per_kwh, plus_tax_eur_per_kwh, total_eur_per_kwh, source, color
 * </pre>
 *
 * <ul>
 *   <li>{@code timestamp} — ISO-8601 with offset (e.g. {@code 2025-06-16T08:00:00Z}); the
 *       UNIQUE upsert key.</li>
 *   <li>{@code source} — {@code ESIOS} or {@code MANUAL}.</li>
 *   <li>{@code color} — optional {@code GREEN|AMBER|RED}; empty means "no override".</li>
 * </ul>
 *
 * <p>Export writes a UTF-8 BOM so Microsoft Excel (Spanish locale) opens the file correctly.
 */
public final class CsvService {

    public static final String[] HEADER = {
            "timestamp", "price_eur_per_kwh", "plus_tax_eur_per_kwh",
            "total_eur_per_kwh", "source", "color"
    };

    /** A single parsed/validated line of the file. */
    public record CsvRow(long lineNumber, PriceRecord record, List<String> errors, List<String> warnings) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /** Full result of a parse: per-row results plus file-level errors (bad header, I/O). */
    public record CsvParseResult(List<CsvRow> rows, List<String> fileErrors) {
        public List<CsvRow> valid() {
            return rows.stream().filter(CsvRow::isValid).toList();
        }

        public List<CsvRow> invalid() {
            return rows.stream().filter(r -> !r.isValid()).toList();
        }
    }

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .setCommentMarker('#')
            .setDelimiter(',')
            .build();

    private CsvService() {
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Parses and validates a CSV stream. UTF-8 is assumed and a leading BOM is stripped before
     * the header is compared against {@link #HEADER}.
     */
    public static CsvParseResult parse(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public static CsvParseResult parse(Reader reader) throws IOException {
        List<CsvRow> rows = new ArrayList<>();
        List<String> fileErrors = new ArrayList<>();

        CSVParser parser = FORMAT.parse(newBomStrippingReader(reader));
        // FORMAT.setHeader() throws on token-count mismatch before we can use try-with-resources,
        // so it is wrapped below.
        try (CSVParser p = parser) {
            List<String> header = p.getHeaderNames();
            if (!Arrays.equals(header.toArray(String[]::new), HEADER)) {
                fileErrors.add("Cabecera no válida. Se esperaba: "
                        + String.join(",", HEADER) + " — se encontró: " + String.join(",", header));
                return new CsvParseResult(List.of(), fileErrors);
            }

            for (CSVRecord rec : p) {
                rows.add(parseRow(rec));
            }
        } catch (IOException e) {
            fileErrors.add("Error de formato CSV (línea " + lineHint(e) + "): " + e.getMessage());
        }
        return new CsvParseResult(rows, fileErrors);
    }

    private static long lineHint(IOException e) {
        return -1L;
    }

    private static CsvRow parseRow(CSVRecord rec) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        OffsetDateTime timestamp = parseIso(rec.get(0), errors, "timestamp");
        BigDecimal price = parseDecimal(rec.get(1), errors, "price_eur_per_kwh");
        BigDecimal plusTax = parseNullableDecimal(rec.get(2), errors, "plus_tax_eur_per_kwh");
        BigDecimal total = parseDecimal(rec.get(3), errors, "total_eur_per_kwh");
        String source = rec.get(4);
        String color = rec.get(5);

        if (price != null && price.signum() < 0) {
            errors.add("price_eur_per_kwh no puede ser negativo");
        }
        if (plusTax != null && plusTax.signum() < 0) {
            errors.add("plus_tax_eur_per_kwh no puede ser negativo");
        }
        if (total != null && total.signum() < 0) {
            errors.add("total_eur_per_kwh no puede ser negativo");
        }
        if (!source.equalsIgnoreCase(PriceRecord.SOURCE_ESIOS)
                && !source.equalsIgnoreCase(PriceRecord.SOURCE_MANUAL)) {
            errors.add("source debe ser ESIOS o MANUAL");
        }
        if (!color.isBlank()
                && !color.equalsIgnoreCase(PriceRecord.COLOR_GREEN)
                && !color.equalsIgnoreCase(PriceRecord.COLOR_AMBER)
                && !color.equalsIgnoreCase(PriceRecord.COLOR_RED)) {
            errors.add("color debe ser GREEN, AMBER, RED o vacío");
        }
        if (price != null && plusTax != null && total != null) {
            BigDecimal expected = price.add(plusTax).setScale(6, RoundingMode.HALF_UP);
            if (expected.subtract(total).abs().compareTo(new BigDecimal("0.0005")) > 0) {
                warnings.add("total no coincide con price + plus_tax (esperado " + expected.toPlainString() + ")");
            }
        }

        PriceRecord record = errors.isEmpty()
                ? new PriceRecord(0L, timestamp, price, plusTax, total,
                        source == null || source.isBlank() ? PriceRecord.SOURCE_ESIOS : source.toUpperCase(Locale.ROOT),
                        color.isBlank() ? null : color.toUpperCase(Locale.ROOT))
                : null;

        return new CsvRow(rec.getRecordNumber(), record, errors, warnings);
    }

    private static OffsetDateTime parseIso(String value, List<String> errors, String label) {
        try {
            return java.time.OffsetDateTime.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            try {
                return java.time.LocalDateTime.parse(value)
                        .toInstant(java.time.ZoneOffset.UTC)
                        .atOffset(java.time.ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                errors.add(label + " no es un timestamp ISO-8601 válido: '" + value + "'");
                return null;
            }
        }
    }

    private static BigDecimal parseDecimal(String value, List<String> errors, String label) {
        BigDecimal parsed = parseNullableDecimal(value, errors, label);
        if (parsed == null && !value.isBlank()) {
            errors.add(label + " no es un número decimal válido: '" + value + "'");
        }
        return parsed;
    }

    private static BigDecimal parseNullableDecimal(String value, List<String> errors, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            errors.add(label + " no es un número decimal válido: '" + value + "'");
            return null;
        }
    }

    // ---------------------------------------------------------------- serialization

    /**
     * Writes the header plus rows as RFC 4180 CSV with a UTF-8 BOM (for Excel in Spanish
     * locales). Empty optional columns are written as empty fields.
     */
    public static void write(OutputStream out, List<PriceRecord> records) throws IOException {
        Objects.requireNonNull(records, "records");
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write("\uFEFF");
        try (CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {
            printer.printRecord((Object[]) HEADER);
            for (PriceRecord r : records) {
                printer.printRecord((Object[]) format(r));
            }
            printer.flush();
        }
    }

    /** Serializes to a String (used by tests and previews). */
    public static String serialize(List<PriceRecord> records) throws IOException {
        StringWriter buffer = new StringWriter();
        // write(String-based) without BOM: StringWriter has no charset; BOM would be a char.
        try (CSVPrinter printer = new CSVPrinter(buffer, FORMAT)) {
            printer.printRecord((Object[]) HEADER);
            for (PriceRecord r : records) {
                printer.printRecord((Object[]) format(r));
            }
        }
        return buffer.toString();
    }

    private static String[] format(PriceRecord r) {
        String tax = r.getPlusTax() == null ? "" : r.getPlusTax().setScale(6, RoundingMode.HALF_UP).toPlainString();
        return new String[]{
                r.getTimestamp() == null ? "" : r.getTimestamp().toInstant().toString(),
                r.getPrice() == null ? "" : r.getPrice().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                tax,
                r.getTotal() == null ? "" : r.getTotal().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                r.getSourceOrDefault(),
                r.getColor() == null ? "" : r.getColor()
        };
    }

    // ---------------------------------------------------------------- BOM handling

    /**
     * Wraps a reader so a leading UTF-8 BOM ({@code \uFEFF}) is dropped before CSV parsing.
     * Also handles the (unlikely but real) case the caller supplies a plain {@link Reader}.
     */
    private static Reader newBomStrippingReader(Reader reader) throws IOException {
        PushbackReader pushback = new PushbackReader(new BufferedReader(reader), 1);
        int first = pushback.read();
        if (first != -1 && first != '\uFEFF') {
            pushback.unread(first);
        }
        return pushback;
    }
}