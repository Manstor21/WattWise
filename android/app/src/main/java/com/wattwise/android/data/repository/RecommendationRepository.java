package com.wattwise.android.data.repository;

import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.RecommendationEntity;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.ApiErrorParser;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.data.remote.dto.RecommendationDto;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.GsonProvider;
import com.wattwise.android.util.Prefs;
import com.wattwise.android.util.PriceUtils;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/**
 * Carga y almacena en caché las recomendaciones del servidor. Cuando la API no
 * está disponible se sirven las filas en caché (último sync) marcadas como offline.
 */
public class RecommendationRepository {

    private static final Type WINDOW_TYPE = new com.google.gson.reflect.TypeToken<List<PriceDto>>() {
    }.getType();

    private final WattWiseDatabase db;
    private final Prefs prefs;

    public RecommendationRepository(WattWiseDatabase db, Prefs prefs) {
        this.db = db;
        this.prefs = prefs;
    }

    public void getAllCached(Callback<List<RecommendationEntity>> callback) {
        AppExecutors.io(() -> {
            List<RecommendationEntity> list = db.recommendationDao().getAll();
            AppExecutors.main(() -> callback.onSuccess(list));
        });
    }

    /** Lectura bloqueante de Room (para fragmentos que prerenderizan desde caché). */
    public List<RecommendationEntity> getAllCachedSync() {
        return db.recommendationDao().getAll();
    }

    /** Descarga del servidor y luego entrega la lista fresca. */
    public void refresh(Callback<List<RecommendationEntity>> callback) {
        AppExecutors.io(() -> {
            try {
                Response<List<RecommendationDto>> r = ApiClient.api().getRecommendations().execute();
                if (!r.isSuccessful() || r.body() == null) {
                    onRefreshFailure(r, callback);
                    return;
                }
                replaceCache(r.body());
                long now = System.currentTimeMillis();
                prefs.setRecommendationLastUpdatedMs(now);
                AppExecutors.main(() -> callback.onSuccess(db.recommendationDao().getAll()));
            } catch (Exception e) {
                onRefreshFailure(null, callback);
            }
        });
    }

    private void onRefreshFailure(Response<?> r, Callback<List<RecommendationEntity>> callback) {
        List<RecommendationEntity> cache = db.recommendationDao().getAll();
        boolean auth = r != null && ApiErrorParser.isAuthError(r);
        if (cache.isEmpty()) {
            String msg = r != null ? ApiErrorParser.message(r) : "network";
            AppExecutors.main(() -> callback.onError(auth, msg));
        } else {
            AppExecutors.main(() -> callback.onSuccess(cache));
        }
    }

    private void replaceCache(List<RecommendationDto> dtos) {
        List<RecommendationEntity> entities = new ArrayList<>();
        for (RecommendationDto d : dtos) {
            if (d.getAppliance() == null || d.getAppliance().getId() == null || d.getRecommendedStart() == null) {
                continue;
            }
            RecommendationEntity e = new RecommendationEntity();
            e.applianceId = d.getAppliance().getId();
            e.applianceName = d.getAppliance().getName();
            e.applianceType = d.getType() != null ? d.getType() : d.getAppliance().getType();
            e.recommendedStart = d.getRecommendedStart().format(PriceUtils.ISO);
            e.recommendedEnd = d.getRecommendedEnd() == null
                    ? null
                    : d.getRecommendedEnd().format(PriceUtils.ISO);
            e.recommendedPriceEurPerKwh = d.getRecommendedPriceEurPerKwh();
            e.estimatedCostEur = d.getEstimatedCostEur();
            e.worstCaseCostEur = d.getWorstCaseCostEur();
            e.estimatedSavingsEur = d.getEstimatedSavingsEur();
            e.savingsPercentage = d.getSavingsPercentage();
            e.semaphore = d.getSemaphore();
            e.windowJson = GsonProvider.get().toJson(d.getWindow(), WINDOW_TYPE);
            e.lastUpdated = System.currentTimeMillis();
            entities.add(e);
        }
        db.recommendationDao().deleteAll();
        if (!entities.isEmpty()) {
            db.recommendationDao().insertAll(entities);
        }
    }

    public static List<PriceDto> windowOf(RecommendationEntity e) {
        if (e.windowJson == null || e.windowJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<PriceDto> window = GsonProvider.get().fromJson(e.windowJson, WINDOW_TYPE);
            return window == null ? new ArrayList<>() : window;
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    public static LocalDateTime parseStart(RecommendationEntity e) {
        return e.recommendedStart == null ? null : PriceUtils.parseUtc(e.recommendedStart);
    }

    public static LocalDateTime parseEnd(RecommendationEntity e) {
        return e.recommendedEnd == null ? null : PriceUtils.parseUtc(e.recommendedEnd);
    }
}