package com.wattwise.service;

import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.BacktestMonthlyDto;
import com.wattwise.model.dto.BacktestReportDto;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.recommendation.RecommendationStrategy;
import com.wattwise.recommendation.StrategyFactory;
import com.wattwise.util.TrafficLightClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Simula el motor de optimización existente sobre un histórico de precios PVPC y
 * cuantifica el ahorro de programar los electrodomésticos en su ventana óptima.
 *
 * <p>Para cada día del histórico se clasifican los 96 slots con
 * {@link TrafficLightClassifier} y, sobre la mezcla fija de electrodomésticos, se
 * resuelve la ventana óptima con {@link RecommendationStrategy} vía
 * {@link StrategyFactory}. El coste naïve (sin optimizar) se documenta como
 * «ejecutar un ciclo diario al precio medio del día»; el coste optimizado usa la
 * ventana contigua más barata que devuelve el motor. Todos los supuestos se exponen
 * como lista de cadenas del informe.
 */
@Service
public class BacktestService {

    /** Número máximo de meses de histórico que soporta el dataset de ejemplo. */
    public static final int MAX_MONTHS = 12;

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String DATA_SOURCE = "Dataset de ejemplo incluido en el repositorio "
            + "(backend/src/main/resources/backtest/pvpc-history-sample.csv): 12 meses de precios PVPC "
            + "simulados deterministas, slots de 15 minutos en UTC. Con ESIOS_API_TOKEN real e ingesta "
            + "diaria en ejecución durante 12 meses, el mismo pipeline funciona sobre datos reales.";

    private final BacktestCsvLoader csvLoader;
    private final TrafficLightClassifier classifier;
    private final StrategyFactory strategyFactory;

    public BacktestService(BacktestCsvLoader csvLoader, TrafficLightClassifier classifier,
                           StrategyFactory strategyFactory) {
        this.csvLoader = csvLoader;
        this.classifier = classifier;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Genera el informe de backtesting para los N meses más recientes del dataset.
     *
     * @param months número de meses a analizar (1..12)
     * @return el informe agregado con desglose mensual
     * @throws ValidationException si {@code months} está fuera de rango o no hay datos
     */
    public BacktestReportDto generateReport(int months) {
        List<PriceRecord> records = csvLoader.loadAll();
        return buildReport(records, months);
    }

    /**
     * Construye el informe a partir de una lista de registros ya cargada. Visible a
     * nivel de paquete para que los tests puedan inyectar datos conocidos.
     */
    BacktestReportDto buildReport(List<PriceRecord> records, int months) {
        if (records == null || records.isEmpty()) {
            throw new ValidationException("No hay registros de precios para generar el informe de backtesting");
        }
        int uniqueMonths = records.stream()
                .map(record -> YearMonth.from(record.getDate()))
                .collect(Collectors.toSet())
                .size();
        if (months < 1) {
            throw new ValidationException("El parámetro 'months' debe ser mayor o igual a 1");
        }
        if (months > Math.min(uniqueMonths, MAX_MONTHS)) {
            throw new ValidationException("El parámetro 'months' (" + months + ") supera los datos "
                    + "disponibles (" + Math.min(uniqueMonths, MAX_MONTHS) + " meses)");
        }

        // Agrupación por fecha UTC: cada día debe contener sus 96 slots completos.
        Map<LocalDate, List<PriceRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(PriceRecord::getDate, TreeMap::new,
                        Collectors.toList()));

        List<YearMonth> allMonths = new ArrayList<>(byDate.keySet().stream()
                .map(YearMonth::from)
                .distinct()
                .sorted()
                .collect(Collectors.toList()));
        List<YearMonth> selectedMonths = allMonths.subList(allMonths.size() - months, allMonths.size());

        Map<YearMonth, List<LocalDate>> datesByMonth = new LinkedHashMap<>();
        for (YearMonth month : selectedMonths) {
            datesByMonth.put(month, byDate.keySet().stream()
                    .filter(date -> YearMonth.from(date).equals(month))
                    .collect(Collectors.toList()));
        }

        BigDecimal naiveTotal = BigDecimal.ZERO;
        BigDecimal optimizedTotal = BigDecimal.ZERO;
        int daysAnalyzed = 0;
        List<BacktestMonthlyDto> breakdown = new ArrayList<>();

        for (YearMonth month : selectedMonths) {
            BigDecimal monthNaive = BigDecimal.ZERO;
            BigDecimal monthOptimized = BigDecimal.ZERO;
            for (LocalDate date : datesByMonth.get(month)) {
                DayResult result = simulateDay(byDate.get(date));
                monthNaive = monthNaive.add(result.naive());
                monthOptimized = monthOptimized.add(result.optimized());
            }
            daysAnalyzed += datesByMonth.get(month).size();
            naiveTotal = naiveTotal.add(monthNaive);
            optimizedTotal = optimizedTotal.add(monthOptimized);
            breakdown.add(new BacktestMonthlyDto(
                    month.format(MONTH_FORMAT),
                    roundEur(monthNaive),
                    roundEur(monthOptimized),
                    roundEur(monthNaive.subtract(monthOptimized)),
                    percentage(monthNaive.subtract(monthOptimized), monthNaive)));
        }

        BacktestReportDto report = new BacktestReportDto();
        // El periodo refleja las fechas reales analizadas (primera y última del rango).
        List<LocalDate> firstMonthDates = datesByMonth.get(selectedMonths.get(0));
        List<LocalDate> lastMonthDates = datesByMonth.get(selectedMonths.get(selectedMonths.size() - 1));
        report.setPeriodFrom(firstMonthDates.get(0));
        report.setPeriodTo(lastMonthDates.get(lastMonthDates.size() - 1));
        report.setDaysAnalyzed(daysAnalyzed);
        report.setNaiveCostEur(roundEur(naiveTotal));
        report.setOptimizedCostEur(roundEur(optimizedTotal));
        report.setSavingsEur(roundEur(naiveTotal.subtract(optimizedTotal)));
        report.setSavingsPercentage(percentage(naiveTotal.subtract(optimizedTotal), naiveTotal));
        report.setApplianceMix(applianceMix());
        report.setMonthlyBreakdown(breakdown);
        report.setAssumptions(assumptions());
        report.setDataSource(DATA_SOURCE);
        log.info("Backtest generado: {} días entre {} y {}, ahorro {} %",
                report.getDaysAnalyzed(), report.getPeriodFrom(), report.getPeriodTo(),
                report.getSavingsPercentage());
        return report;
    }

    /** Simula un día completo: clasifica los slots y resuelve la ventana óptima de cada electrodoméstico. */
    private DayResult simulateDay(List<PriceRecord> dayRecords) {
        List<PriceRecord> sorted = dayRecords.stream()
                .sorted(Comparator.comparing(PriceRecord::getTimestamp))
                .toList();
        List<BigDecimal> prices = sorted.stream()
                .map(PriceRecord::getTotalEurPerKwh)
                .toList();
        List<TrafficLight> colors = classifier.classifyDay(prices);
        List<PriceDto> dayPrices = new ArrayList<>(prices.size());
        for (int i = 0; i < sorted.size(); i++) {
            PriceRecord record = sorted.get(i);
            dayPrices.add(new PriceDto(record.getId(), record.getTimestamp(),
                    record.getPriceEurPerKwh(), record.getPlusTaxEurPerKwh(),
                    record.getTotalEurPerKwh(), record.getSource(), colors.get(i)));
        }

        BigDecimal meanPrice = meanTotalPrice(dayPrices);
        BigDecimal naive = BigDecimal.ZERO;
        BigDecimal optimized = BigDecimal.ZERO;
        for (Appliance appliance : fixedMix()) {
            // Política naïve documentada: un ciclo diario al precio medio del día.
            naive = naive.add(appliance.getAvgCycleKwh().multiply(meanPrice));
            RecommendationStrategy strategy = strategyFactory.strategyFor(appliance);
            Optional<RecommendationDto> recommendation = strategy.recommend(appliance, dayPrices);
            if (recommendation.isPresent()) {
                optimized = optimized.add(recommendation.get().getEstimatedCostEur());
            }
        }
        return new DayResult(naive, optimized);
    }

    private BigDecimal meanTotalPrice(List<PriceDto> dayPrices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PriceDto dto : dayPrices) {
            sum = sum.add(dto.getTotalEurPerKwh());
        }
        return sum.divide(BigDecimal.valueOf(dayPrices.size()), 6, RoundingMode.HALF_UP);
    }

    /** Mezcla fija de electrodomésticos: consumes y duraciones del seed V2 del catálogo. */
    private static List<Appliance> fixedMix() {
        return List.of(
                newAppliance(ApplianceType.WASHING_MACHINE, "Lavadora", "1.0", 120),
                newAppliance(ApplianceType.DISHWASHER, "Lavavajillas", "0.9", 90),
                newAppliance(ApplianceType.EV_CHARGER, "Cargador de vehículo eléctrico", "7.0", 240),
                newAppliance(ApplianceType.OTHER, "Electrodoméstico genérico", "1.2", 90));
    }

    private static Appliance newAppliance(ApplianceType type, String name, String kwh, int minutes) {
        Appliance appliance = new Appliance();
        appliance.setType(type);
        appliance.setName(name);
        appliance.setPowerWatts(1000);
        appliance.setAvgCycleKwh(new BigDecimal(kwh));
        appliance.setEstimatedCycleMinutes(minutes);
        appliance.setActive(true);
        return appliance;
    }

    private static List<String> applianceMix() {
        return List.of(
                "Lavadora (WASHING_MACHINE) — 1.0 kWh/ciclo, 120 min",
                "Lavavajillas (DISHWASHER) — 0.9 kWh/ciclo, 90 min",
                "Cargador de vehículo eléctrico (EV_CHARGER) — 7.0 kWh/ciclo, 240 min",
                "Electrodoméstico genérico (OTHER) — 1.2 kWh/ciclo, 90 min");
    }

    private static List<String> assumptions() {
        return List.of(
                "Mezcla fija simulada: lavadora (1.0 kWh/ciclo, 120 min), lavavajillas "
                        + "(0.9 kWh/ciclo, 90 min), cargador de VE (7.0 kWh/ciclo, 240 min) y "
                        + "electrodoméstico genérico (1.2 kWh/ciclo, 90 min); consumos del seed V2 del catálogo.",
                "Cada electrodoméstico ejecuta un ciclo al día.",
                "Política naïve documentada: cada electrodoméstico se ejecuta al precio medio del día "
                        + "(media aritmética de sus slots) multiplicado por su consumo por ciclo, sin "
                        + "optimizar el horario.",
                "El coste optimizado usa la ventana contigua más barata del día según el motor de "
                        + "recomendaciones existente (estrategias por tipo con penalizaciones de semáforo), "
                        + "dentro del día y sin restricciones de disponibilidad del usuario.",
                "La clasificación de semáforo de cada día es independiente: percentil, desviación respecto "
                        + "a la media y umbrales absolutos del TrafficLightClassifier, con suavizado de bordes.",
                "Los días se agrupan por fecha UTC del timestamp; cada día contiene 96 slots de 15 minutos.",
                "No se modelan impuestos, peajes, término fijo de potencia, baterías ni excedentes; el precio "
                        + "total EUR/kWh coincide con el neto porque el CSV solo transporta el valor de mercado.",
                "El dataset de ejemplo es determinista (semilla fija) y sintetiza estacionalidad anual, perfil "
                        + "valle nocturno / punta y un patrón de fin de semana distinto.");
    }

    private static BigDecimal roundEur(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal base) {
        if (base == null || base.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    /** Costes agregados de un día simulado. */
    private record DayResult(BigDecimal naive, BigDecimal optimized) {
    }
}