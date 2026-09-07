package com.wattwise.scheduler;

import com.wattwise.exception.ExternalApiException;
import com.wattwise.model.dto.EsiosPricePoint;
import com.wattwise.service.EsiosClientService;
import com.wattwise.service.PriceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceFetchJobTest {

    @Mock
    private EsiosClientService esiosClientService;

    @Mock
    private PriceService priceService;

    private MeterRegistry meterRegistry;
    private PriceFetchJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new PriceFetchJob(esiosClientService, priceService, meterRegistry);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "maxAttempts", 3);
        ReflectionTestUtils.setField(job, "initialBackoffMs", 1L);
        ReflectionTestUtils.setField(job, "maxBackoffMs", 2L);
    }

    @Test
    void fetchDailyPricesDownloadsTodayAndTomorrow() {
        List<EsiosPricePoint> points = List.of(
                new EsiosPricePoint(LocalDateTime.of(2099, 6, 1, 0, 0), new BigDecimal("100")));
        when(esiosClientService.fetchPricesForDate(any(LocalDate.class))).thenReturn(points);

        job.fetchDailyPrices();

        verify(esiosClientService, times(2)).fetchPricesForDate(any(LocalDate.class));
        verify(priceService, times(2)).saveEsiosPoints(any(LocalDate.class), eq(points));
    }

    @Test
    void fetchDailyPricesNeverRethrowsOnFailure() {
        when(esiosClientService.fetchPricesForDate(any(LocalDate.class)))
                .thenThrow(new ExternalApiException("ESIOS down"));

        // The job swallows errors so the scheduler loop keeps running.
        assertThatCode(job::fetchDailyPrices).doesNotThrowAnyException();
        verify(priceService, never()).saveEsiosPoints(any(), any());
    }

    @Test
    void fetchDailyPricesRecordsSuccessMetrics() {
        List<EsiosPricePoint> points = List.of(
                new EsiosPricePoint(LocalDateTime.of(2099, 6, 1, 0, 0), new BigDecimal("100")));
        when(esiosClientService.fetchPricesForDate(any(LocalDate.class))).thenReturn(points);
        when(priceService.saveEsiosPoints(any(LocalDate.class), eq(points)))
                .thenReturn(List.of(new com.wattwise.model.entity.PriceRecord()));

        job.fetchDailyPrices();

        assertThat(meterRegistry.counter("esiros_fetch_total", "status", "success").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("price_records_inserted_total", "source", "esiros").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("esiros_fetch_total", "status", "error").count()).isZero();
    }

    @Test
    void fetchFailureIncrementsErrorCounter() {
        LocalDate date = LocalDate.of(2099, 6, 1);
        when(esiosClientService.fetchPricesForDate(date)).thenThrow(new ExternalApiException("boom"));

        assertThatCode(() -> job.fetchForDate(date)).doesNotThrowAnyException();
        assertThat(meterRegistry.counter("esiros_fetch_total", "status", "error").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("esiros_fetch_total", "status", "success").count()).isZero();
    }

    @Test
    void fetchWithRetryRetriesAfterTransientFailure() {
        LocalDate date = LocalDate.of(2099, 6, 1);
        List<EsiosPricePoint> points = List.of(
                new EsiosPricePoint(LocalDateTime.of(2099, 6, 1, 0, 0), new BigDecimal("100")));
        when(esiosClientService.fetchPricesForDate(date))
                .thenThrow(new ExternalApiException("transient 502"))
                .thenThrow(new ExternalApiException("transient 502"))
                .thenReturn(points);

        List<EsiosPricePoint> result = job.fetchWithRetry(date);

        assertThat(result).isEqualTo(points);
        verify(esiosClientService, times(3)).fetchPricesForDate(date);
    }

    @Test
    void fetchWithRetryGivesUpAfterMaxAttempts() {
        LocalDate date = LocalDate.of(2099, 6, 1);
        when(esiosClientService.fetchPricesForDate(date))
                .thenThrow(new ExternalApiException("boom"));

        assertThatCode(() -> job.fetchWithRetry(date))
                .isInstanceOf(ExternalApiException.class);
        verify(esiosClientService, times(3)).fetchPricesForDate(date);
    }

    @Test
    void disabledJobDoesNothing() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.fetchDailyPrices();

        verify(esiosClientService, never()).fetchPricesForDate(any());
        verify(priceService, never()).saveEsiosPoints(any(), any());
    }
}