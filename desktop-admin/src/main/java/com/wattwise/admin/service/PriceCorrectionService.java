package com.wattwise.admin.service;

import com.wattwise.admin.db.QueryExecutor;
import com.wattwise.admin.model.PriceRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida y aplica las correcciones manuales de precios desde el editor en línea.
 *
 * <p>Reglas de validación:
 * <ul>
 *   <li>price {@code >= 0};</li>
 *   <li>{@code plus_tax_eur_per_kwh} {@code >= 0} (nullable);</li>
 *   <li>{@code total_eur_per_kwh} {@code >= 0};</li>
 *   <li>{@code color} debe ser {@code GREEN|AMBER|RED} o null;</li>
 *   <li>si {@code price + plus_tax} difiere notablemente de {@code total}, se reporta una
 *       advertencia (informativa, no bloqueante).</li>
 * </ul>
 */
public final class PriceCorrectionService {

    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.0005");

    public record ValidationResult(boolean ok, List<String> errors, List<String> warnings) {
    }

    public record CorrectionReport(int attempted, int updated) {
    }

    private PriceCorrectionService() {
    }

    /** Validación pura, sin acceso a la base de datos — se puede llamar desde los tests. */
    public static ValidationResult validate(PriceRecord record) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        BigDecimal price = record.getPrice();
        BigDecimal plusTax = record.getPlusTax();
        BigDecimal total = record.getTotal();

        if (price == null) {
            errors.add("El precio no puede estar vacío");
        } else if (price.signum() < 0) {
            errors.add("El precio no puede ser negativo");
        }
        if (plusTax != null && plusTax.signum() < 0) {
            errors.add("Los impuestos no pueden ser negativos");
        }
        if (total == null) {
            errors.add("El total no puede estar vacío");
        } else if (total.signum() < 0) {
            errors.add("El total no puede ser negativo");
        }

        String color = record.getColor();
        if (color != null && !color.isBlank()) {
            String upper = color.toUpperCase();
            if (!PriceRecord.COLOR_GREEN.equals(upper)
                    && !PriceRecord.COLOR_AMBER.equals(upper)
                    && !PriceRecord.COLOR_RED.equals(upper)) {
                errors.add("Color no válido: " + color + " (esperado GREEN, AMBER o RED)");
            }
        }

        if (price != null && plusTax != null && total != null) {
            BigDecimal expected = price.add(plusTax).setScale(6, RoundingMode.HALF_UP);
            if (expected.subtract(total).abs().compareTo(TOTAL_TOLERANCE) > 0) {
                warnings.add("El total (" + total.toPlainString() + ") no cuadra con price + plus_tax ("
                        + expected.toPlainString() + ")");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * Persiste las correcciones que pasan la validación. Cada registro se aplica con un único
     * {@code UPDATE ... WHERE id = ?}. Los registros inválidos se omiten y se cuentan aparte.
     */
    public static CorrectionReport apply(Connection connection, List<PriceRecord> changes)
            throws SQLException {
        int attempted = 0;
        int updated = 0;
        for (PriceRecord change : changes) {
            ValidationResult validation = validate(change);
            if (!validation.ok()) {
                continue;
            }
            attempted++;
            updated += QueryExecutor.updatePrice(connection, change);
        }
        return new CorrectionReport(attempted, updated);
    }
}