package com.wattwise.service;

import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.BacktestMonthlyDto;
import com.wattwise.model.dto.BacktestReportDto;
import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.recommendation.DishwasherStrategy;
import com.wattwise.recommendation.EvChargingStrategy;
import com.wattwise.recommendation.GenericApplianceStrategy;
import com.wattwise.recommendation.RecommendationStrategy;
import com.wattwise.recommendation.StrategyFactory;
import com.wattwise.recommendation.WashingMachineStrategy;
import com.wattwise.util.TrafficLightClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del {@link BacktestService}: cálculo correcto con datos conocidos,
 * filtrado por el parámetro {@code months}, presencia de la mezcla de
 * electrodomésticos y de los supuestos, y validación de entradas.
 */
class BacktestServiceTest {

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        List<RecommendationStrategy> strategies = List.of(
                new WashingMachineStrategy(),
                new DishwasherStrategy(),
                new EvChargingStrategy(),
                new GenericApplianceStrategy());
        backtestService = new BacktestService(new BacktestCsvLoader(),
                new TrafficLightClassifier(), new StrategyFactory(strategies));
    }

    @Test
    void reportCalculatesKnownSavingsForASingleDay() {
        // Día con valle nocturno (0,05), tramo medio (0,10), punta (0,15) y pico (0,20).
        // Media diaria = 10,4 / 96 = 0,108333 EUR/kWh.
        double[] pattern = new double[96];
        Arrays.fill(pattern, 0, 32, 0.05);
        Arrays.fill(pattern, 32, 64, 0.10);
        Arrays.fill(pattern, 64, 80, 0.15);
        Arrays.fill(pattern, 80, 96, 0.20);
        List<PriceRecord> day = day(LocalDate.of(2025, 1, 15), pattern);

        BacktestReportDto report = backtestService.buildReport(day, 1);

        assertThat(report.getPeriodFrom()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(report.getPeriodTo()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(report.getDaysAnalyzed()).isEqualTo(1);

        // Coste naïve: Σ kWh x precio medio. Consumos 1,0 + 0,9 + 7,0 + 1,2 = 10,1 kWh.
        assertThat(report.getNaiveCostEur()).isEqualByComparingTo("1.0942");
        // Coste optimizado: todos los electrodomésticos caben en el valle de 0,05.
        assertThat(report.getOptimizedCostEur()).isEqualByComparingTo("0.5050");
        assertThat(report.getSavingsEur()).isEqualByComparingTo("0.5892");
        assertThat(report.getSavingsPercentage()).isEqualByComparingTo("53.85");

        assertThat(report.getMonthlyBreakdown()).hasSize(1);
        BacktestMonthlyDto month = report.getMonthlyBreakdown().get(0);
        assertThat(month.getMonth()).isEqualTo("2025-01");
        assertThat(month.getNaiveCostEur()).isEqualByComparingTo(report.getNaiveCostEur());
        assertThat(month.getSavingsPercentage()).isEqualByComparingTo("53.85");
    }

    @Test
    void reportIncludesApplianceMixAndAssumptions() {
        double[] flat = new double[96];
        Arrays.fill(flat, 0.08);
        BacktestReportDto report = backtestService.buildReport(
                day(LocalDate.of(2025, 6, 1), flat), 1);

        assertThat(report.getApplianceMix()).hasSize(4)
                .anyMatch(mix -> mix.contains("Lavadora"))
                .anyMatch(mix -> mix.contains("EV_CHARGER"));
        assertThat(report.getAssumptions())
                .isNotEmpty()
                .anyMatch(a -> a.contains("Política naïve"))
                .anyMatch(a -> a.contains("ventana contigua más barata"));
        assertThat(report.getDataSource()).contains("pvpc-history-sample.csv");
    }

    @Test
    void reportKeepsOnlyTheMostRecentMonthsRequested() {
        List<PriceRecord> records = new ArrayList<>();
        records.addAll(day(LocalDate.of(2025, 1, 5), flatDay(0.10)));
        records.addAll(day(LocalDate.of(2025, 2, 5), flatDay(0.10)));
        records.addAll(day(LocalDate.of(2025, 3, 5), flatDay(0.10)));

        BacktestReportDto twoMonths = backtestService.buildReport(records, 2);
        assertThat(twoMonths.getMonthlyBreakdown()).hasSize(2);
        assertThat(twoMonths.getMonthlyBreakdown().get(0).getMonth()).isEqualTo("2025-02");
        assertThat(twoMonths.getMonthlyBreakdown().get(1).getMonth()).isEqualTo("2025-03");
        assertThat(twoMonths.getDaysAnalyzed()).isEqualTo(2);
        assertThat(twoMonths.getPeriodFrom()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(twoMonths.getPeriodTo()).isEqualTo(LocalDate.of(2025, 3, 5));

        BacktestReportDto oneMonth = backtestService.buildReport(records, 1);
        assertThat(oneMonth.getMonthlyBreakdown()).hasSize(1);
        assertThat(oneMonth.getMonthlyBreakdown().get(0).getMonth()).isEqualTo("2025-03");
    }

    @Test
    void uniformCheapDayYieldsNoSavings() {
        double[] flat = new double[96];
        Arrays.fill(flat, 0.05);
        List<PriceRecord> day = day(LocalDate.of(2025, 4, 10), flat);

        BacktestReportDto report = backtestService.buildReport(day, 1);

        // Todo el día es verde profundo: optimizar no aporta nada frente a la media.
        assertThat(report.getOptimizedCostEur()).isEqualByComparingTo(report.getNaiveCostEur());
        assertThat(report.getSavingsEur()).isEqualByComparingTo("0.0000");
        assertThat(report.getSavingsPercentage()).isEqualByComparingTo("0.00");
    }

    @Test
    void reportRejectsInvalidMonths() {
        double[] flat = new double[96];
        Arrays.fill(flat, 0.10);
        List<PriceRecord> records = new ArrayList<>();
        records.addAll(day(LocalDate.of(2025, 1, 5), flat));
        records.addAll(day(LocalDate.of(2025, 2, 5), flat));
        records.addAll(day(LocalDate.of(2025, 3, 5), flat));

        assertThatThrownBy(() -> backtestService.buildReport(records, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("mayor o igual a 1");
        assertThatThrownBy(() -> backtestService.buildReport(records, 4))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("supera los datos disponibles");
    }

    @Test
    void reportRejectsEmptyData() {
        assertThatThrownBy(() -> backtestService.buildReport(List.of(), 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No hay registros de precios");
    }

    private static double[] flatDay(double price) {
        double[] pattern = new double[96];
        Arrays.fill(pattern, price);
        return pattern;
    }

    private static List<PriceRecord> day(LocalDate date, double... totals) {
        List<PriceRecord> records = new ArrayList<>(totals.length);
        for (int slot = 0; slot < totals.length; slot++) {
            PriceRecord record = new PriceRecord();
            record.setTimestamp(date.atStartOfDay().plusMinutes(15L * slot));
            record.setPriceEurPerKwh(BigDecimal.valueOf(totals[slot]));
            record.setTotalEurPerKwh(BigDecimal.valueOf(totals[slot]));
            record.setSource(PriceSource.ESIOS);
            records.add(record);
        }
        return records;
    }
}