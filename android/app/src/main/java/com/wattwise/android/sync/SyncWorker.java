package com.wattwise.android.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.RecommendationEntity;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.data.remote.dto.RecommendationDto;
import com.wattwise.android.data.repository.ApplianceRepository;
import com.wattwise.android.data.repository.PriceRepository;
import com.wattwise.android.data.repository.RecommendationRepository;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

/**
 * One-shot worker that:
 * <ol>
 *   <li>pushes pending appliance rows to the server (LWW via
 *       {@link ConflictResolver});</li>
 *   <li>pulls the server appliance snapshot;</li>
 *   <li>refreshes recommendations and currencies the three-day price cache.</li>
 * </ol>
 * A 401 anywhere clears the session (the next screen visit redirects to login).
 */
public class SyncWorker extends Worker {

    private static final String WORK_NAME = "sync-now";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Fire-and-forget schedule used by SettingsFragment and after login. */
    public static void enqueue(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setInitialDelay(10, TimeUnit.SECONDS) // let the UI settle first
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Prefs prefs = new Prefs(ctx);
        SessionManager session = new SessionManager(ctx);
        if (!session.hasSession()) {
            return Result.success(); // nothing to sync for anonymous users
        }

        WattWiseDatabase db = WattWiseDatabase.get(ctx);
        ApplianceRepository appliances = new ApplianceRepository(db);
        PriceRepository prices = new PriceRepository(db, prefs);

        boolean[] auth = new boolean[1];
        boolean appliancesOk = appliances.sync(auth);
        if (auth[0]) {
            session.clear();
            return Result.failure();
        }

        try {
            refreshRecommendations(db, prefs, session);
            String from = LocalDate.now(PriceUtils.SPAIN).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            String to = LocalDate.now(PriceUtils.SPAIN).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            Response<List<PriceDto>> range = ApiClient.api().getPricesRange(from, to).execute();
            if (range.isSuccessful() && range.body() != null) {
                prices.cacheApiPrices(range.body());
                prefs.setPriceLastUpdatedMs(System.currentTimeMillis());
            }
            return Result.success();
        } catch (IOException e) {
            // Transient network error — schedule a retry later.
            return Result.retry();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    private void refreshRecommendations(WattWiseDatabase db, Prefs prefs, SessionManager session) {
        try {
            Response<List<RecommendationDto>> r = ApiClient.api().getRecommendations().execute();
            if (!r.isSuccessful()) {
                if (r.code() == 401 || r.code() == 403) {
                    session.clear();
                }
                return;
            }
            List<RecommendationEntity> entities = new ArrayList<>();
            for (RecommendationDto d : r.body() == null ? new ArrayList<RecommendationDto>() : r.body()) {
                if (d.getAppliance() == null || d.getAppliance().getId() == null || d.getRecommendedStart() == null) {
                    continue;
                }
                RecommendationEntity e = new RecommendationEntity();
                e.applianceId = d.getAppliance().getId();
                e.applianceName = d.getAppliance().getName();
                e.applianceType = d.getType() != null ? d.getType() : d.getAppliance().getType();
                e.recommendedStart = d.getRecommendedStart().format(PriceUtils.ISO);
                e.recommendedEnd = d.getRecommendedEnd() == null ? null : d.getRecommendedEnd().format(PriceUtils.ISO);
                e.recommendedPriceEurPerKwh = d.getRecommendedPriceEurPerKwh();
                e.estimatedCostEur = d.getEstimatedCostEur();
                e.worstCaseCostEur = d.getWorstCaseCostEur();
                e.estimatedSavingsEur = d.getEstimatedSavingsEur();
                e.savingsPercentage = d.getSavingsPercentage();
                e.semaphore = d.getSemaphore();
                e.windowJson = com.wattwise.android.util.GsonProvider.get().toJson(d.getWindow());
                e.lastUpdated = System.currentTimeMillis();
                entities.add(e);
            }
            db.recommendationDao().deleteAll();
            if (!entities.isEmpty()) {
                db.recommendationDao().insertAll(entities);
            }
            prefs.setRecommendationLastUpdatedMs(System.currentTimeMillis());
        } catch (IOException ignored) {
            // Recommendations are best-effort in this worker.
        }
    }
}