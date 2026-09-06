package com.wattwise.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared price-domain helpers used by services, the scheduler and the
 * recommendation engine.
 */
public final class PriceUtils {

    /** Spanish peninsula / mainland offset used by the PVPC market. */
    public static final ZoneId SPAIN_ZONE = ZoneId.of("Europe/Madrid");

    private PriceUtils() {
    }

    /** Parse an ISO-8601 string to a UTC LocalDateTime. Fast-fails on garbage. */
    public static LocalDateTime parseIsoUtc(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            throw new IllegalArgumentException("datetime is null or empty");
        }
        Instant instant = Instant.parse(datetime);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Convert a LocalDateTime (treated as Spanish local time) to UTC LocalDateTime. */
    public static LocalDateTime toUtc(LocalDateTime spainLocal) {
        return spainLocal.atZone(SPAIN_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Convert a UTC LocalDateTime to Spain local wall-clock time. */
    public static LocalDateTime toSpain(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(SPAIN_ZONE).toLocalDateTime();
    }

    /** Return the Spanish-local date of a UTC slot. Used to bucket records by day. */
    public static LocalDate spainDateOf(LocalDateTime utc) {
        return toSpain(utc).toLocalDate();
    }

    /** MWh → kWh per unit (ESIOS publishes EUR/MWh). */
    public static BigDecimal eurPerMwhToEurPerKwh(BigDecimal eurPerMwh) {
        return eurPerMwh.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    /** Sum a list of kwh values. */
    public static BigDecimal sumKwh(List<BigDecimal> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Compute the minimum value in a list. */
    public static BigDecimal min(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Compute the maximum value in a list. */
    public static BigDecimal max(List<BigDecimal> values) {
        return values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Duration of a slot given its start time (different measurement granularity selection). */
    public static long slotDurationMinutes(LocalDateTime start) {
        return 15;
    }

    /** Sort prices ascending (used to determine best/worst slot). */
    public static List<PriceRecordLike> sortAscending(List<PriceRecordLike> records) {
        return records.stream()
                .sorted(Comparator.comparing(r -> r.totalEurPerKwh()))
                .collect(Collectors.toList());
    }

    /** Minimal structural view of a price record so this util depends only on data, not JPA. */
    public interface PriceRecordLike {
        LocalDateTime timestamp();
        BigDecimal totalEurPerKwh();
    }
}