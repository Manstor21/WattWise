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
 * Time and formatting helpers shared by the UI, workers and repositories.
 *
 * <h2>Timezone strategy (documented decision)</h2>
 * The backend serialises every {@code timestamp} as an ISO-8601 {@code LocalDateTime}
 * WITHOUT a zone suffix, e.g. {@code 2025-01-05T23:00:00}. The values are stored in
 * UTC on the server. Two workable parsers exist:
 * <ul>
 *   <li>{@link LocalDateTime#parse} (treat the naive value as UTC) and shift to
 *       {@code Europe/Madrid};</li>
 *   <li>{@link java.time.OffsetDateTime#parse} with a forced {@code +00:00} offset,
 *       then the same zone shift.</li>
 * </ul>
 * Both converge to the exact same instant; we use {@link LocalDateTime#parse}
 * because the input has no offset component and this avoids inventing a zone in the
 * parser. All display conversions go through {@link #SPAIN}.
 */
public final class PriceUtils {

    public static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    public static final ZoneId UTC = ZoneId.of("UTC");

    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES"));

    private PriceUtils() {
    }

    /** Parses a backend timestamp (UTC, no zone suffix) as a UTC {@link LocalDateTime}. */
    public static LocalDateTime parseUtc(String iso) {
        return LocalDateTime.parse(iso, ISO);
    }

    /** Shifts a naive-UTC timestamp to the Spanish wall time. */
    public static LocalDateTime utcToSpain(LocalDateTime utc) {
        return utc.atZone(UTC).withZoneSameInstant(SPAIN).toLocalDateTime();
    }

    /** Shifts a Spanish wall time back to naive-UTC (used when writing requests). */
    public static LocalDateTime spainToUtc(LocalDateTime local) {
        return local.atZone(SPAIN).withZoneSameInstant(UTC).toLocalDateTime();
    }

    /** The Spanish calendar date a slot belongs to (after converting its UTC instant). */
    public static LocalDate spanishDateOfSlot(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).toLocalDate();
    }

    /** "HH:mm" of a slot in Spanish wall time (start of the 15-minute slot). */
    public static String slotStartLabel(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).format(TIME);
    }

    /** "HH:mm" one slot later — the end of a 15-minute slot. */
    public static String slotEndLabel(LocalDateTime utcTimestamp) {
        return utcToSpain(utcTimestamp).plusMinutes(15).format(TIME);
    }

    /** Price with dot decimal separator, mirroring the web client's toFixed() output. */
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

    /** Sum helper for the dashboard mean computation. */
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

    /** Grouping key "yyyy-MM-dd" of the Spanish date a slot belongs to. */
    public static String spanishDayKey(LocalDateTime utcTimestamp) {
        return spanishDateOfSlot(utcTimestamp).toString();
    }

    /** Negligible helper reused by the scheduler: wall-time window label. */
    public static String windowLabel(LocalDateTime utcStart, LocalDateTime utcEnd) {
        return utcToSpain(utcStart).format(TIME) + " – " + utcToSpain(utcEnd).format(TIME);
    }

    /** Midnight as a LocalTime constant for hour-slot grouping helpers. */
    public static LocalTime midnight() {
        return LocalTime.MIDNIGHT;
    }
}