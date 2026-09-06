package com.wattwise.service;

import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.EsiosPricePoint;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.repository.PriceRecordRepository;
import com.wattwise.util.TrafficLightClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private PriceRecordRepository priceRecordRepository;

    private PriceService priceService;

    @BeforeEach
    void setUp() {
        priceService = new PriceService(priceRecordRepository, new TrafficLightClassifier());
    }

    private PriceRecord record(LocalDateTime ts, double total) {
        PriceRecord r = new PriceRecord();
        r.setTimestamp(ts);
        r.setPriceEurPerKwh(BigDecimal.valueOf(total));
        r.setTotalEurPerKwh(BigDecimal.valueOf(total));
        r.setSource(PriceSource.ESIOS);
        return r;
    }

    @Test
    void getTodayClassifiesRecordsAndAppendsColor() {
        LocalDateTime base = LocalDateTime.of(2025, 1, 5, 0, 0);
        List<PriceRecord> records = List.of(
                record(base, 0.05),
                record(base.plusMinutes(15), 0.12),
                record(base.plusMinutes(30), 0.30));
        when(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(records);

        List<PriceDto> dtos = priceService.getToday();

        assertThat(dtos).hasSize(3);
        assertThat(dtos.get(0).getColor()).isEqualTo(TrafficLight.GREEN);
        assertThat(dtos.get(2).getColor()).isEqualTo(TrafficLight.RED);
        assertThat(dtos.get(0).getTotalEurPerKwh()).isEqualByComparingTo("0.05");
        assertThat(dtos.get(0).getSource()).isEqualTo(PriceSource.ESIOS);
    }

    @Test
    void getTomorrowReturnsEmptyWhenNothingPublished() {
        when(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of());
        assertThat(priceService.getTomorrow()).isEmpty();
    }

    @Test
    void getRangeRejectsInvalidWindow() {
        assertThatThrownBy(() -> priceService.getRange(
                LocalDate.of(2025, 1, 10), LocalDate.of(2025, 1, 5)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> priceService.getRange(null, LocalDate.of(2025, 1, 5)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void getRangeDelegatesToRepositoryWithExpandedWindow() {
        List<PriceRecord> records = List.of(record(LocalDateTime.of(2025, 1, 5, 0, 0), 0.10));
        when(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(records);

        List<PriceDto> dtos = priceService.getRange(
                LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5));

        assertThat(dtos).hasSize(1);
        verify(priceRecordRepository).findByTimestampBetweenOrderByTimestampAsc(any(), any());
    }

    @Test
    void saveEsiosPointsConvertsMwhToKwhAndDedupes() {
        // 100 EUR/MWh → 0.100000 EUR/kWh; 30000 EUR/MWh → 30 EUR/kWh.
        EsiosPricePoint p1 = new EsiosPricePoint(LocalDateTime.of(2025, 1, 5, 0, 0), new BigDecimal("100"));
        EsiosPricePoint p2 = new EsiosPricePoint(LocalDateTime.of(2025, 1, 5, 0, 15), new BigDecimal("30000"));
        when(priceRecordRepository.existsByTimestamp(p1.datetime())).thenReturn(false);
        when(priceRecordRepository.existsByTimestamp(p2.datetime())).thenReturn(true); // duplicate
        when(priceRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PriceRecord> saved = priceService.saveEsiosPoints(LocalDate.of(2025, 1, 5), List.of(p1, p2));

        assertThat(saved).hasSize(1);
        PriceRecord r = saved.get(0);
        assertThat(r.getTotalEurPerKwh()).isEqualByComparingTo("0.100000");
        assertThat(r.getSource()).isEqualTo(PriceSource.ESIOS);
        verify(priceRecordRepository).save(any());
    }

    @Test
    void todayMeanIsArithmeticAverageOfTotalPrices() {
        LocalDateTime base = LocalDateTime.of(2025, 1, 5, 0, 0);
        List<PriceRecord> records = List.of(
                record(base, 0.08),
                record(base.plusMinutes(15), 0.10),
                record(base.plusMinutes(30), 0.12));
        when(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any())).thenReturn(records);

        BigDecimal mean = priceService.getTodayMeanEurPerKwh();

        assertThat(mean).isEqualByComparingTo(new BigDecimal("0.100000"));
    }

    @Test
    void todayMeanIsZeroWhenNoPrices() {
        when(priceRecordRepository.findByTimestampBetweenOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of());
        assertThat(priceService.getTodayMeanEurPerKwh()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}