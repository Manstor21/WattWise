package com.wattwise.service;

import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.EsiosPricePoint;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.repository.PriceRecordRepository;
import com.wattwise.util.PriceUtils;
import com.wattwise.util.TrafficLightClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consulta e ingesta de precios. Posee la conversión de los puntos crudos de ESIOS
 * (EUR/MWh) a {@link PriceRecord} persistidos y aplica la clasificación de semáforo
 * antes de devolver los DTO.
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final PriceRecordRepository priceRecordRepository;
    private final TrafficLightClassifier classifier;

    public PriceService(PriceRecordRepository priceRecordRepository, TrafficLightClassifier classifier) {
        this.priceRecordRepository = priceRecordRepository;
        this.classifier = classifier;
    }

    /** Precios del día español actual, clasificados. */
    @Transactional(readOnly = true)
    public List<PriceDto> getToday() {
        return getForSpainDate(LocalDate.now(PriceUtils.SPAIN_ZONE));
    }

    /** Precios de mañana (vacíos si aún no se han publicado). */
    @Transactional(readOnly = true)
    public List<PriceDto> getTomorrow() {
        return getForSpainDate(LocalDate.now(PriceUtils.SPAIN_ZONE).plusDays(1));
    }

    /** Precios dentro de un rango de fechas inclusivo [from, to] (fechas españolas). */
    @Transactional(readOnly = true)
    public List<PriceDto> getRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("Both 'from' and 'to' query params are required (yyyy-MM-dd)");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("'from' must not be after 'to'");
        }
        LocalDateTime fromUtc = PriceUtils.toUtc(from.atStartOfDay());
        LocalDateTime toUtc = PriceUtils.toUtc(to.plusDays(1).atStartOfDay());
        return toDtos(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(fromUtc, toUtc));
    }

    /** Precios clasificados para una fecha local española concreta (agrupados por fecha española). */
    @Transactional(readOnly = true)
    public List<PriceDto> getForSpainDate(LocalDate spainDate) {
        LocalDateTime fromUtc = PriceUtils.toUtc(spainDate.atStartOfDay());
        LocalDateTime toUtc = PriceUtils.toUtc(spainDate.plusDays(1).atStartOfDay());
        List<PriceRecord> records = priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(fromUtc, toUtc);
        return toDtos(records);
    }

    /** Precio medio (EUR/kWh) del día español actual; 0 si no hay ninguno. Lo usan los observers de alertas. */
    @Transactional(readOnly = true)
    public BigDecimal getTodayMeanEurPerKwh() {
        List<PriceDto> today = getToday();
        if (today.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = today.stream()
                .map(PriceDto::getTotalEurPerKwh)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(today.size()), 6, java.math.RoundingMode.HALF_UP);
    }

    /** Hace upsert de puntos crudos de ESIOS (EUR/MWh) en PriceRecords para una fecha española. */
    @Transactional
    public List<PriceRecord> saveEsiosPoints(LocalDate spainDate, List<EsiosPricePoint> points) {
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        List<PriceRecord> result = points.stream().map(point -> {
            LocalDateTime utc = point.datetime();
            if (priceRecordRepository.existsByTimestamp(utc)) {
                skipped.incrementAndGet();
                return null;
            }
            BigDecimal totalEurPerKwh = PriceUtils.eurPerMwhToEurPerKwh(point.valueEurPerMwh());
            PriceRecord record = new PriceRecord();
            record.setTimestamp(utc);
            record.setPriceEurPerKwh(totalEurPerKwh); // aproximado; el valor ESIOS incluye peajes
            record.setPlusTaxEurPerKwh(null);
            record.setTotalEurPerKwh(totalEurPerKwh);
            record.setSource(PriceSource.ESIOS);
            saved.incrementAndGet();
            return priceRecordRepository.save(record);
        }).filter(java.util.Objects::nonNull).toList();

        log.info("Saved {} new price records for {} ({} duplicates skipped)", saved.get(), spainDate, skipped.get());
        return result;
    }

    private List<PriceDto> toDtos(List<PriceRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        // Se agrupa por fecha española para que cada día se clasifique de forma independiente.
        return records.stream()
                .collect(java.util.stream.Collectors.groupingBy(r -> PriceUtils.spainDateOf(r.getTimestamp()),
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(entry -> {
                    List<TrafficLight> colors = classifier.classifyDay(
                            entry.getValue().stream().map(PriceRecord::getTotalEurPerKwh).toList());
                    List<PriceDto> dtos = new java.util.ArrayList<>();
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        dtos.add(toDto(entry.getValue().get(i), colors.get(i)));
                    }
                    return dtos.stream();
                })
                .toList();
    }

    private PriceDto toDto(PriceRecord record, TrafficLight color) {
        return new PriceDto(record.getId(), record.getTimestamp(),
                record.getPriceEurPerKwh(), record.getPlusTaxEurPerKwh(),
                record.getTotalEurPerKwh(), record.getSource(), color);
    }
}