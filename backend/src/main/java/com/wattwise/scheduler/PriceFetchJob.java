package com.wattwise.scheduler;

import com.wattwise.exception.ExternalApiException;
import com.wattwise.model.dto.EsiosPricePoint;
import com.wattwise.service.EsiosClientService;
import com.wattwise.service.PriceService;
import com.wattwise.util.PriceUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Tarea programada que descarga los precios PVPC de hoy y mañana desde ESIOS
 * (publicados a diario en torno a las 20:15 CET) y los persiste.
 *
 * <p><b>Resiliencia:</b> cada fecha se obtiene con reintentos de backoff exponencial
 * (configurable). Un fallo total se registra como alerta y nunca se relanza, de modo que
 * el hilo del scheduler sigue vivo para la siguiente ejecución. Los últimos precios
 * conocidos permanecen en la BD (la API sirve datos "obsoletos" con elegancia).
 */
@Component
public class PriceFetchJob {

    private static final Logger log = LoggerFactory.getLogger(PriceFetchJob.class);

    private final EsiosClientService esiosClientService;
    private final PriceService priceService;
    private final Counter esiosFetchSuccess;
    private final Counter esiosFetchError;
    private final Counter priceRecordsInserted;
    private volatile long lastSuccessfulFetchEpochSec;

    @Value("${wattwise.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${wattwise.esios.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${wattwise.esios.retry.initial-backoff-ms:2000}")
    private long initialBackoffMs;

    @Value("${wattwise.esios.retry.max-backoff-ms:30000}")
    private long maxBackoffMs;

    public PriceFetchJob(EsiosClientService esiosClientService, PriceService priceService, MeterRegistry meterRegistry) {
        this.esiosClientService = esiosClientService;
        this.priceService = priceService;
        // Observabilidad (modules.md §7): contadores expuestos en /actuator/prometheus.
        this.esiosFetchSuccess = meterRegistry.counter("esiros_fetch_total", "status", "success");
        this.esiosFetchError = meterRegistry.counter("esiros_fetch_total", "status", "error");
        this.priceRecordsInserted = meterRegistry.counter("price_records_inserted_total", "source", "esiros");
        // Gauge que alimenta el panel de "obsolescencia de datos" (dashboard wattwise-price-pipeline).
        // Sincronizado sobre el volatile mutable para que Prometheus lea el último valor.
        meterRegistry.gauge("esiros_last_fetch_success_timestamp", this, PriceFetchJob::lastSuccessfulFetchEpochSec);
    }

    /** Cron "0 15 20 * * *" = 20:15:00 todos los días (hora de España). */
    @Scheduled(cron = "${wattwise.scheduler.price-fetch-cron:0 15 20 * * *}")
    public void fetchDailyPrices() {
        if (!enabled) {
            log.debug("PriceFetchJob disabled via config");
            return;
        }
        LocalDate today = LocalDate.now(PriceUtils.SPAIN_ZONE);
        fetchForDate(today);
        fetchForDate(today.plusDays(1));
    }

    /** Obtiene y persiste una única fecha; nunca propaga fallos. */
    void fetchForDate(LocalDate date) {
        boolean success = false;
        try {
            List<EsiosPricePoint> points = fetchWithRetry(date);
            int inserted = priceService.saveEsiosPoints(date, points).size();
            priceRecordsInserted.increment(inserted);
            lastSuccessfulFetchEpochSec = Instant.now().getEpochSecond();
            success = true;
        } catch (ExternalApiException ex) {
            log.error("[ALERT] ESIOS price fetch failed for {}: {}", date, ex.getMessage());
        } finally {
            // Registra siempre el resultado tras un intento, incluso ante excepciones inesperadas.
            (success ? esiosFetchSuccess : esiosFetchError).increment();
        }
    }

    /** Bucle de reintentos con backoff lineal-exponencial alrededor del cliente ESIOS mockeable. */
    List<EsiosPricePoint> fetchWithRetry(LocalDate date) {
        long backoffMs = initialBackoffMs;
        ExternalApiException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return esiosClientService.fetchPricesForDate(date);
            } catch (ExternalApiException ex) {
                last = ex;
                log.warn("ESIOS fetch attempt {}/{} failed for {}: {}", attempt, maxAttempts, date, ex.getMessage());
                if (attempt < maxAttempts) {
                    sleepSafely(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
                }
            }
        }
        throw last != null ? last : new ExternalApiException("ESIOS fetch failed for " + date);
    }

    private void sleepSafely(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    long lastSuccessfulFetchEpochSec() {
        return lastSuccessfulFetchEpochSec;
    }
}