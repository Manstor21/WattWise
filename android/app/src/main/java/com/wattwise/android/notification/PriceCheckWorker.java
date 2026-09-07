package com.wattwise.android.notification;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.data.repository.PriceRepository;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * Periodic worker (every 6h — the real minimum for WorkManager periodic work is
 * 15 minutes, we deliberately use 6h to match the marketing cadence) that:
 * <ol>
 *   <li>Fetches today + tomorrow prices and upserts them into Room;</li>
 *   <li>compares the day's cheapest slots against the user's threshold (mean
 *       minus configured %);</li>
 *   <li>posts a local notification for the optimal future window and/or a low
 *       price slot, deduplicated by slot start so a 6h cadence doesn't spam.</li>
 * </ol>
 */
public class PriceCheckWorker extends Worker {

    /** Default sliding window (in hours) used to build the "optimal window" alert. */
    public static final double DEFAULT_WINDOW_HOURS = 2.0;

    private static final String WORK_NAME = "price-check";

    private final Prefs prefs;

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.prefs = new Prefs(context);
    }

    /** Enqueues (or replaces) the periodic run from the app entry point. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                PriceCheckWorker.class, 6L, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        WattWiseDatabase db = WattWiseDatabase.get(ctx);
        PriceRepository repo = new PriceRepository(db, prefs);

        List<PriceDto> today;
        List<PriceDto> tomorrow;
        try {
            today = fetch(repo, ctx, true);
            tomorrow = fetch(repo, ctx, false);
        } catch (IOException e) {
            return Result.retry();
        }

        if (!prefs.alertsEnabled()) {
            return Result.success();
        }

        List<PriceDto> all = new ArrayList<>();
        if (today != null) {
            all.addAll(today);
        }
        if (tomorrow != null) {
            all.addAll(tomorrow);
        }

        try {
            evaluateAndNotify(ctx, all);
        } catch (RuntimeException ignored) {
            // A malformed slot must never crash the worker; next run retries.
        }
        return Result.success();
    }

    private List<PriceDto> fetch(PriceRepository repo, Context ctx, boolean today) throws IOException {
        Response<List<PriceDto>> response = today
                ? ApiClient.api().getTodayPrices().execute()
                : ApiClient.api().getTomorrowPrices().execute();
        List<PriceDto> body = null;
        if (response.isSuccessful()) {
            body = response.body();
        }
        if (body != null) {
            // Cache into Room regardless of alert state — the dashboard profits.
            repo.cacheApiPrices(body);
        }
        return body == null ? new ArrayList<>() : body;
    }

    private void evaluateAndNotify(Context ctx, List<PriceDto> all) {
        BigDecimal mean = OptimalWindowScheduler.meanPrice(all);
        int pct = prefs.alertThresholdPct();
        BigDecimal threshold = mean
                .multiply(BigDecimal.valueOf(100 - pct))
                .divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);

        // 1) Single-slot low price notification.
        PriceDto lowest = OptimalWindowScheduler.lowestPrice(all);
        if (lowest != null
                && lowest.getTimestamp() != null
                && OptimalWindowScheduler.hasPriceBelow(all, threshold)) {
            String key = lowest.getTimestamp().format(PriceUtils.ISO);
            if (!key.equals(prefs.lastNotifiedWindowStart())) {
                NotificationHelper.notifyLowPrice(
                        ctx,
                        PriceUtils.formatPrice(lowest.getTotalEurPerKwh()),
                        PriceUtils.slotStartLabel(lowest.getTimestamp()));
                prefs.setLastNotifiedWindowStart(key);
            }
        }

        // 2) Optimal future window notification.
        List<PriceDto> future = OptimalWindowScheduler.futureSlots(
                all, java.time.LocalDateTime.now(PriceUtils.UTC));
        OptimalWindowScheduler.Window window =
                OptimalWindowScheduler.findCheapestWindow(future, DEFAULT_WINDOW_HOURS);
        if (window != null
                && (window.semaphore.equals("GREEN") || window.averageEurPerKwh.compareTo(threshold) < 0)) {
            String key = window.startUtc.format(PriceUtils.ISO);
            if (!key.equals(prefs.lastNotifiedWindowStart())) {
                NotificationHelper.notifyOptimalWindow(
                        ctx,
                        PriceUtils.slotStartLabel(window.startUtc),
                        PriceUtils.slotEndLabel(window.endUtc.minusMinutes(15)),
                        PriceUtils.formatPrice(window.averageEurPerKwh));
                prefs.setLastNotifiedWindowStart(key);
            }
        }
    }
}