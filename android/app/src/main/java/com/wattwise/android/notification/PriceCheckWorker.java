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
 * Worker periódico (cada 6 h — el mínimo real para el trabajo periódico de
 * WorkManager es 15 minutos; usamos 6 h deliberadamente para coincidir con la
 * cadencia de marketing) que:
 * <ol>
 *   <li>obtiene los precios de hoy y mañana y los inserta o actualiza en Room;</li>
 *   <li>compara los slots más baratos del día con el umbral del usuario (media
 *       menos el % configurado);</li>
 *   <li>publica una notificación local para la ventana futura óptima y/o un slot
 *       de precio bajo, deduplicada por inicio de slot para que la cadencia de 6 h
 *       no haga spam.</li>
 * </ol>
 */
public class PriceCheckWorker extends Worker {

    /** Ventana deslizante por defecto (en horas) usada para construir la alerta de "ventana óptima". */
    public static final double DEFAULT_WINDOW_HOURS = 2.0;

    private static final String WORK_NAME = "price-check";

    private final Prefs prefs;

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.prefs = new Prefs(context);
    }

    /** Encola (o reemplaza) la ejecución periódica desde el punto de entrada de la app. */
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
            // Un slot malformado nunca debe tumbar el worker; el siguiente run reintenta.
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
            // Guardar en Room con independencia del estado de alertas — el resumen se beneficia.
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

        // 1) Notificación de precio bajo en un único slot.
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

        // 2) Notificación de ventana futura óptima.
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