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
 * Análisis y serialización CSV (RFC 4180) con validación estricta para la importación de
 * precios de WattWise.
 *
 * <p>Disposición de columnas (se mantiene como el único contrato entre la plantilla, el
 * analizador y el exportador — las columnas {@code id} y {@code date} derivadas del esquema
 * no forman parte del archivo intencionadamente):
 *
 * <pre>
 * timestamp, price_eur_per_kwh, plus_tax_eur_per_kwh, total_eur_per_kwh, source, color
 * </pre>
 *
 * <ul>
 *   <li>{@code timestamp} — ISO-8601 con offset (p. ej. {@code 2025-06-16T08:00:00Z}); la
 *       clave de upsert UNIQUE.</li>
 *   <li>{@code source} — {@code ESIOS} o {@code MANUAL}.</li>
 *   <li>{@code color} — opcional {@code GREEN|AMBER|RED}; vacío significa "sin override".</li>
 * </ul>
 *
 * <p>La exportación escribe una BOM UTF-8 para que Microsoft Excel (configuración regional
 * de español) abra el archivo correctamente.
 */
public final class CsvService {

    public static final String[] HEADER = {
            "timestamp", "price_eur_per_kwh", "plus_tax_eur_per_kwh",
            "total_eur_per_kwh", "source", "color"
    };

    /** Una única línea analizada/validada del archivo. */
    public record CsvRow(long lineNumber, PriceRecord record, List<String> errors, List<String> warnings) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    /** Resultado completo de un análisis: resultados por fila más errores a nivel de archivo (cabecera incorrecta, E/S). */
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

    // ---------------------------------------------------------------- análisis

    /**
     * Analiza y valida un flujo CSV. Se asume UTF-8 y se elimina una BOM inicial antes de
     * comparar la cabecera con {@link #HEADER}.
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
        // FORMAT.setHeader() lanza una excepción si el número de campos no coincide antes de poder
        // usar try-with-resources, por lo que se envuelve abajo.
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

    // ---------------------------------------------------------------- serialización

    /**
     * Escribe la cabecera y las filas como CSV RFC 4180 con BOM UTF-8 (para Excel en locales de
     * español). Las columnas opcionales vacías se escriben como campos vacíos.
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

    /** Serializa a un String (lo usan los tests y las vistas previas). */
    public static String serialize(List<PriceRecord> records) throws IOException {
        StringWriter buffer = new StringWriter();
        // write (basado en String) sin BOM: StringWriter no tiene charset; la BOM sería un carácter.
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

    // ---------------------------------------------------------------- manejo de BOM

    /**
     * Envuelve un reader para que se elimine una BOM UTF-8 inicial ({@code \uFEFF}) antes del
     * análisis CSV. También cubre el caso (improbable pero real) de que la llamada suministre un
     * {@link Reader} simple.
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