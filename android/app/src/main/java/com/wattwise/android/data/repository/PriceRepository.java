package com.wattwise.android.data.repository;

import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.PriceRecordEntity;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.ApiErrorParser;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * Offline-first access to price slots. The strategy:
 * <ol>
 *   <li>Always try the API on a refresh request.</li>
 *   <li>On success, upsert everything into Room and purge slots older than
 *       three days.</li>
 *   <li>On failure, serve the cached slots for the requested Spanish date and
 *       flag the result as {@code offline} so the UI can show the banner.</li>
 * </ol>
 */
public class PriceRepository {

    private static final int CACHE_DAYS = 3;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final WattWiseDatabase db;
    private final Prefs prefs;

    public PriceRepository(WattWiseDatabase db, Prefs prefs) {
        this.db = db;
        this.prefs = prefs;
    }

    /**
     * Public entry for workers to persist freshly-fetched price lists straight
     * into Room (no callback, no UI result). Also runs the cache purge window.
     */
    public void cacheApiPrices(List<PriceDto> prices) {
        if (prices != null && !prices.isEmpty()) {
            upsert(prices);
        }
    }

    /** Loads cached slots synchronously (call on an IO thread). */
    public List<PriceDto> cachedForDate(LocalDate madridDate) {
        String target = madridDate.toString();
        List<PriceDto> result = new ArrayList<>();
        for (PriceRecordEntity e : db.priceDao().getAll()) {
            LocalDateTime utc = PriceUtils.parseUtc(e.timestamp);
            if (PriceUtils.spanishDayKey(utc).equals(target)) {
                result.add(toDto(e));
            }
        }
        return result;
    }

    /**
     * Fetches today+tomorrow (+history to fill the cache window) and delivers the
     * slots for {@code targetDate} (Spanish calendar day).
     */
    public void fetchDay(LocalDate targetDate, Callback<DayResult> callback) {
        AppExecutors.io(() -> {
            try {
                LocalDate from = targetDate.minusDays(1);
                LocalDate to = targetDate.plusDays(1);
                Response<List<PriceDto>> response = ApiClient.api()
                        .getPricesRange(from.format(ISO_DATE), to.format(ISO_DATE))
                        .execute();
                if (response.isSuccessful() && response.body() != null) {
                    upsert(response.body());
                    long now = System.currentTimeMillis();
                    prefs.setPriceLastUpdatedMs(now);
                    AppExecutors.main(() -> callback.onSuccess(
                            new DayResult(cachedForDate(targetDate), false, now)));
                } else {
                    handleFailure(response, targetDate, callback);
                }
            } catch (Exception e) {
                handleFailure(null, targetDate, callback);
            }
        });
    }

    private void handleFailure(Response<?> response, LocalDate targetDate, Callback<DayResult> callback) {
        List<PriceDto> cache = cachedForDate(targetDate);
        boolean authError = response != null && ApiErrorParser.isAuthError(response);
        if (cache.isEmpty() && response != null) {
            String msg = ApiErrorParser.message(response);
            AppExecutors.main(() -> callback.onError(authError, msg));
        } else if (cache.isEmpty()) {
            AppExecutors.main(() -> callback.onError(false, "network"));
        } else {
            long last = prefs.priceLastUpdatedMs();
            AppExecutors.main(() -> callback.onSuccess(new DayResult(cache, true, last)));
        }
    }

    private void upsert(List<PriceDto> prices) {
        List<PriceRecordEntity> entities = new ArrayList<>();
        LocalDate today = LocalDate.now(PriceUtils.SPAIN);
        for (PriceDto p : prices) {
            if (p.getTimestamp() == null) {
                continue;
            }
            PriceRecordEntity e = new PriceRecordEntity();
            e.timestamp = p.getTimestamp().format(PriceUtils.ISO);
            e.serverId = p.getId() == null ? -1L : p.getId();
            e.priceEurPerKwh = p.getPriceEurPerKwh();
            e.plusTaxEurPerKwh = p.getPlusTaxEurPerKwh();
            e.totalEurPerKwh = p.getTotalEurPerKwh();
            e.source = p.getSource();
            e.color = p.getColor();
            entities.add(e);
        }
        db.priceDao().insertAll(entities);

        // Purge anything outside the 3-day caching window.
        LocalDate cutoff = today.minusDays(CACHE_DAYS);
        // The last slot of the cutoff day is cutoff+1 00:00 local → convert to UTC string.
        LocalDateTime cutoffUtc = cutoff.atStartOfDay().atZone(PriceUtils.SPAIN)
                .withZoneSameInstant(PriceUtils.UTC).toLocalDateTime();
        db.priceDao().deleteOlderThan(cutoffUtc.format(PriceUtils.ISO));
    }

    public static PriceDto toDto(PriceRecordEntity e) {
        PriceDto dto = new PriceDto();
        dto.setId(e.serverId < 0 ? null : e.serverId);
        dto.setTimestamp(PriceUtils.parseUtc(e.timestamp));
        dto.setPriceEurPerKwh(e.priceEurPerKwh);
        dto.setPlusTaxEurPerKwh(e.plusTaxEurPerKwh);
        dto.setTotalEurPerKwh(e.totalEurPerKwh);
        dto.setSource(e.source);
        dto.setColor(e.color);
        return dto;
    }

    /** Bundle delivered to the UI after {@link #fetchDay}. */
    public static final class DayResult {
        public final List<PriceDto> slots;
        public final boolean offline;
        public final long lastUpdatedMs;

        DayResult(List<PriceDto> slots, boolean offline, long lastUpdatedMs) {
            this.slots = slots;
            this.offline = offline;
            this.lastUpdatedMs = lastUpdatedMs;
        }
    }
}