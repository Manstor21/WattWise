package com.wattwise.android.data.repository;

import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.entity.ApplianceEntity;
import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.ApiErrorParser;
import com.wattwise.android.data.remote.dto.ApplianceDto;
import com.wattwise.android.sync.ConflictResolver;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;
import com.wattwise.android.util.PriceUtils;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

import retrofit2.Response;

/**
 * Offline-first appliance CRUD.
 *
 * <p>Writes are staged locally ({@code isPendingSync}) and only reach the server
 * through {@link com.wattwise.android.sync.SyncWorker}. Reads merge server
 * snapshots into Room using {@link ConflictResolver} (last-write-wins).
 */
public class ApplianceRepository {

    private final WattWiseDatabase db;

    public ApplianceRepository(WattWiseDatabase db) {
        this.db = db;
    }

    // ------------------------------------------------------------------
    // Local reads (UI)
    // ------------------------------------------------------------------

    public void getAllActive(Callback<List<ApplianceEntity>> callback) {
        AppExecutors.io(() -> {
            List<ApplianceEntity> list = db.applianceDao().getAllActive();
            AppExecutors.main(() -> callback.onSuccess(list));
        });
    }

    // ------------------------------------------------------------------
    // Local writes (staged for sync)
    // ------------------------------------------------------------------

    public void createLocal(String name, String type, int powerWatts,
                            BigDecimal avgCycleKwh, int estimatedCycleMinutes,
                            boolean active, Callback<ApplianceEntity> callback) {
        AppExecutors.io(() -> {
            ApplianceEntity e = new ApplianceEntity();
            e.name = name;
            e.type = type;
            e.powerWatts = powerWatts;
            e.avgCycleKwh = avgCycleKwh;
            e.estimatedCycleMinutes = estimatedCycleMinutes;
            e.isActive = active;
            e.isPendingSync = true;
            e.isDeleted = false;
            e.updatedAt = System.currentTimeMillis();
            e.localId = db.applianceDao().insert(e);
            AppExecutors.main(() -> callback.onSuccess(e));
        });
    }

    public void updateLocal(ApplianceEntity entity, Callback<ApplianceEntity> callback) {
        AppExecutors.io(() -> {
            entity.isPendingSync = true;
            entity.updatedAt = System.currentTimeMillis();
            db.applianceDao().insert(entity);
            AppExecutors.main(() -> callback.onSuccess(entity));
        });
    }

    public void deleteLocal(ApplianceEntity entity, Callback<Void> callback) {
        AppExecutors.io(() -> {
            if (entity.serverId == null) {
                // Never reached the server — nothing to delete remotely.
                db.applianceDao().deleteRow(entity.localId);
            } else {
                db.applianceDao().markDeleted(entity.localId, System.currentTimeMillis());
            }
            AppExecutors.main(() -> callback.onSuccess(null));
        });
    }

    // ------------------------------------------------------------------
    // Sync (used by SyncWorker)
    // ------------------------------------------------------------------

    /**
     * Push pending rows then pull the server snapshot, resolving conflicts with
     * last-write-wins. Returns false with {@code authError=true} on 401/403.
     */
    public boolean sync(boolean[] authErrorOut) {
        try {
            pushPending();
            pullFromServer();
            return true;
        } catch (UnauthorizedException e) {
            authErrorOut[0] = true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sync used directly by the UI on app start (fire-and-forget refresh). */
    public void refresh(Callback<List<ApplianceEntity>> callback) {
        AppExecutors.io(() -> {
            boolean[] auth = new boolean[1];
            boolean ok = sync(auth);
            List<ApplianceEntity> list = db.applianceDao().getAllActive();
            if (ok) {
                AppExecutors.main(() -> callback.onSuccess(list));
            } else if (auth[0]) {
                AppExecutors.main(() -> callback.onError(true, "unauthorized"));
            } else {
                AppExecutors.main(() -> callback.onSuccess(list)); // offline: cache is enough
            }
        });
    }

    private void pushPending() throws Exception {
        for (ApplianceEntity e : db.applianceDao().getPending()) {
            boolean hasServerId = e.serverId != null;
            if (!ConflictResolver.needsServerAction(e.isPendingSync, e.isDeleted, hasServerId)) {
                continue;
            }
            if (hasServerId && e.isDeleted) {
                Response<Void> r = ApiClient.api().deleteAppliance(e.serverId).execute();
                requireOk(r);
                db.applianceDao().deleteRow(e.localId); // tombstone consumed
            } else if (hasServerId) {
                Response<ApplianceDto> r = ApiClient.api()
                        .updateAppliance(e.serverId, toDto(e)).execute();
                requireOk(r);
                db.applianceDao().markSynced(e.localId, r.body().getId(), System.currentTimeMillis());
            } else {
                Response<ApplianceDto> r = ApiClient.api()
                        .createAppliance(toDto(e)).execute();
                requireOk(r);
                db.applianceDao().markSynced(e.localId, r.body().getId(), System.currentTimeMillis());
            }
        }
    }

    private void pullFromServer() throws Exception {
        Response<List<ApplianceDto>> r = ApiClient.api().getAppliances().execute();
        requireOk(r);
        if (r.body() == null) {
            return;
        }
        for (ApplianceDto d : r.body()) {
            if (d.getId() == null) {
                continue;
            }
            ApplianceEntity local = db.applianceDao().getByServerId(d.getId());
            if (local == null) {
                db.applianceDao().insert(cleanEntity(d));
            } else {
                long serverWrite = d.getCreatedAt() == null
                        ? -1L
                        : d.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
                if (ConflictResolver.resolve(local.updatedAt, serverWrite) == ConflictResolver.Winner.REMOTE) {
                    // Server snapshot is newer/authoritative — adopt it.
                    db.applianceDao().insert(cleanEntity(d, local.serverId, local.localId));
                }
                // LOCAL wins → keep the staged pending row untouched.
            }
        }
    }

    private static void requireOk(Response<?> r) throws UnauthorizedException {
        if (!r.isSuccessful()) {
            if (ApiErrorParser.isAuthError(r)) {
                throw new UnauthorizedException();
            }
            throw new RuntimeException("HTTP " + r.code() + " " + ApiErrorParser.message(r));
        }
    }

    private static ApplianceEntity cleanEntity(ApplianceDto d) {
        return cleanEntity(d, d.getId(), 0L);
    }

    private static ApplianceEntity cleanEntity(ApplianceDto d, Long serverId, long localId) {
        ApplianceEntity e = new ApplianceEntity();
        e.localId = localId;
        e.serverId = serverId;
        e.name = d.getName();
        e.type = d.getType();
        e.powerWatts = d.getPowerWatts();
        e.avgCycleKwh = d.getAvgCycleKwh();
        e.estimatedCycleMinutes = d.getEstimatedCycleMinutes();
        e.isActive = d.getIsActive() == null || d.getIsActive();
        e.isPendingSync = false;
        e.isDeleted = false;
        e.updatedAt = d.getCreatedAt() == null
                ? System.currentTimeMillis()
                : d.getCreatedAt().atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        return e;
    }

    public static ApplianceDto toDto(ApplianceEntity e) {
        ApplianceDto dto = new ApplianceDto();
        dto.setName(e.name);
        dto.setType(e.type);
        dto.setPowerWatts(e.powerWatts);
        dto.setAvgCycleKwh(e.avgCycleKwh);
        dto.setEstimatedCycleMinutes(e.estimatedCycleMinutes);
        dto.setIsActive(e.isActive);
        return dto;
    }

    /** Internal signal for 401/403 during a sync pass. */
    private static final class UnauthorizedException extends Exception {
    }
}