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
 * Helpers compartidos del dominio de precios usados por services, el scheduler y
 * el motor de recomendaciones.
 */
public final class PriceUtils {

    /** Offset de la península / territorio continental español usado por el mercado PVPC. */
    public static final ZoneId SPAIN_ZONE = ZoneId.of("Europe/Madrid");

    private PriceUtils() {
    }

    /** Parsea una cadena ISO-8601 a un LocalDateTime en UTC. Falla rápido ante basura. */
    public static LocalDateTime parseIsoUtc(String datetime) {
        if (datetime == null || datetime.isBlank()) {
            throw new IllegalArgumentException("datetime is null or empty");
        }
        Instant instant = Instant.parse(datetime);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Convierte un LocalDateTime (tratado como hora local española) a un LocalDateTime en UTC. */
    public static LocalDateTime toUtc(LocalDateTime spainLocal) {
        return spainLocal.atZone(SPAIN_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Convierte un LocalDateTime en UTC a la hora local de pared española. */
    public static LocalDateTime toSpain(LocalDateTime utc) {
        return utc.atZone(ZoneOffset.UTC).withZoneSameInstant(SPAIN_ZONE).toLocalDateTime();
    }

    /** Devuelve la fecha local española de un slot en UTC. Se usa para agrupar los registros por día. */
    public static LocalDate spainDateOf(LocalDateTime utc) {
        return toSpain(utc).toLocalDate();
    }

    /** MWh → kWh por unidad (ESIOS publica en EUR/MWh). */
    public static BigDecimal eurPerMwhToEurPerKwh(BigDecimal eurPerMwh) {
        return eurPerMwh.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    /** Suma una lista de valores en kWh. */
    public static BigDecimal sumKwh(List<BigDecimal> values) {
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Calcula el valor mínimo de una lista. */
    public static BigDecimal min(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Calcula el valor máximo de una lista. */
    public static BigDecimal max(List<BigDecimal> values) {
        return values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    /** Duración de un slot dado su momento de inicio (selección de granularidad de medición distinta). */
    public static long slotDurationMinutes(LocalDateTime start) {
        return 15;
    }

    /** Ordena los precios de forma ascendente (se usa para determinar el mejor/peor slot). */
    public static List<PriceRecordLike> sortAscending(List<PriceRecordLike> records) {
        return records.stream()
                .sorted(Comparator.comparing(r -> r.totalEurPerKwh()))
                .collect(Collectors.toList());
    }

    /** Vista estructural mínima de un registro de precio para que este util dependa solo de datos, no de JPA. */
    public interface PriceRecordLike {
        LocalDateTime timestamp();
        BigDecimal totalEurPerKwh();
    }
}