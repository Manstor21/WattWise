package com.wattwise.android.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Helpers de tiempo y formato compartidos por la UI, los workers y los repositorios.
 *
 * <h2>Estrategia de zona horaria (decisión documentada)</h2>
 * El backend serializa cada {@code timestamp} como un {@code LocalDateTime} ISO-8601
 * SIN sufijo de zona, p. ej. {@code 2025-01-05T23:00:00}. Los valores se almacenan en
 * UTC en el servidor. Hay dos analizadores viables:
 * <ul>
 *   <li>{@link LocalDateTime#parse} (tratar el valor naive como UTC) y desplazarlo a
 *       {@code Europe/Madrid};</li>
 *   <li>{@link java.time.OffsetDateTime#parse} con un offset forzado de {@code +00:00},
 *       y después el mismo desplazamiento de zona.</li>
 * </ul>
 * Ambos convergen al mismo instante exacto; usamos {@link LocalDateTime#parse}
 * porque la entrada no tiene componente de offset y esto evita inventar una zona en el
 * analizador. Todas las conversiones para mostrar pasan por {@link #SPAIN}.
 */
public final class PriceUtils {

    public static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    public static final ZoneId UTC = ZoneId.of("UTC");

    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES"));

    private PriceUtils() {
    }

    /** Analiza un timestamp del backend (UTC, sin sufijo de zona) como {@link LocalDateTime} en UTC. */
    public static LocalDateTime parseUtc(String iso) {
        return LocalDateTime.parse(iso, ISO);
    }

    /** Desplaza un timestamp naive-UTC a la hora local de España. */
    public static LocalDateTime utcToSpain(LocalDateTime utc) {
        return utc.atZone(UTC).withZoneSameInstant(SPAIN).toLocalDateTime();
    }

    /** Desplaza una hora local de España de vuelta a naive-UTC (usado al escribir peticiones). */
    public static LocalDateTime spainToUtc(LocalDateTime local) {
        return local.atZone(SPAIN).withZoneSameInstant(UTC).toLocalDateTime();
    }

    /** La fecha del calendario español a la que pertenece un slot (tras convertir su instante UTC). */
    public static LocalDate spanishDateOfSlot(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).toLocalDate();
    }

    /** "HH:mm" de un slot en hora local de España (inicio del slot de 15 minutos). */
    public static String slotStartLabel(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).format(TIME);
    }

    /** "HH:mm" un slot más tarde — el final de un slot de 15 minutos. */
    public static String slotEndLabel(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).plusMinutes(15).format(TIME);
    }

    /** Precio con separador decimal de punto, replicando la salida toFixed() del cliente web. */
    public static String formatPrice(BigDecimal pricePerKwh) {
        if (pricePerKwh == null) {
            return "--";
        }
        return new DecimalFormat("#,##0.0000", DecimalFormatSymbols.getInstance(Locale.US))
                .format(pricePerKwh.doubleValue());
    }

    public static String formatPriceEuro(BigDecimal pricePerKwh) {
        return formatPrice(pricePerKwh) + " €/kWh";
    }

    public static String formatEuros(BigDecimal amount) {
        if (amount == null) {
            return "--";
        }
        return String.format(Locale.US, "%.2f €", amount.doubleValue());
    }

    public static String formatPercent(BigDecimal pct) {
        if (pct == null) {
            return "--";
        }
        return new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US))
                .format(pct.doubleValue()) + " %";
    }

    public static String formatLastUpdated(LocalDateTime utcTimestamp) {
        LocalDateTime local = utcToSpain(utcTimestamp);
        return local.toLocalDate().format(DATE) + " · " + local.format(TIME);
    }

    /** Helper de suma para el cálculo de la media del resumen. */
    public static BigDecimal mean(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (BigDecimal v : values) {
            if (v != null) {
                sum = sum.add(v);
                n++;
            }
        }
        if (n == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
    }

    /** Clave de agrupación "yyyy-MM-dd" de la fecha española a la que pertenece un slot. */
    public static String spanishDayKey(LocalDateTime utcTimestamp) {
        return spanishDateOfSlot(utcTimestamp).toString();
    }

    /** Helper trivial reutilizado por el planificador: etiqueta de ventana en hora local. */
    public static String windowLabel(LocalDateTime utcStart, LocalDateTime utcEnd) {
        return utcToSpain(utcStart).format(TIME) + " – " + utcToSpain(utcEnd).format(TIME);
    }

    /** Medianoche como constante LocalTime para los helpers de agrupación por horas. */
    public static LocalTime midnight() {
        return LocalTime.MIDNIGHT;
    }
}