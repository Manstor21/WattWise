package com.wattwise.scheduler;

import com.wattwise.exception.ExternalApiException;
import com.wattwise.model.dto.EsiosPricePoint;
import com.wattwise.service.EsiosClientService;
import com.wattwise.service.PriceService;
import com.wattwise.util.PriceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that downloads today's and tomorrow's PVPC prices from ESIOS
 * (published daily around 20:15 CET) and persists them.
 *
 * <p><b>Resilience:</b> each date is fetched with exponential backoff retries
 * (configurable). A total failure is logged as an alert and never rethrown, so
 * the scheduler thread stays alive for the next run. The last-known prices stay
 * in the DB (the API serves "stale" data gracefully).
 */
@Component
public class PriceFetchJob {

    private static final Logger log = LoggerFactory.getLogger(PriceFetchJob.class);

    private final EsiosClientService esiosClientService;
    private final PriceService priceService;

    @Value("${wattwise.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${wattwise.esios.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${wattwise.esios.retry.initial-backoff-ms:2000}")
    private long initialBackoffMs;

    @Value("${wattwise.esios.retry.max-backoff-ms:30000}")
    private long maxBackoffMs;

    public PriceFetchJob(EsiosClientService esiosClientService, PriceService priceService) {
        this.esiosClientService = esiosClientService;
        this.priceService = priceService;
    }

    /** Cron "0 15 20 * * *" = 20:15:00 every day (Spain time). */
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

    /** Fetch+persist a single date; never propagates failures. */
    void fetchForDate(LocalDate date) {
        try {
            List<EsiosPricePoint> points = fetchWithRetry(date);
            priceService.saveEsiosPoints(date, points);
        } catch (ExternalApiException ex) {
            log.error("[ALERT] ESIOS price fetch failed for {}: {}", date, ex.getMessage());
        }
    }

    /** Linear-to-exponential backoff retry loop around the mockable ESIOS client. */
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
}