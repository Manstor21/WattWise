package com.wattwise.android.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.wattwise.android.data.local.entity.ApplianceEntity;

import java.util.List;

/**
 * CRUD offline-first para electrodomésticos. Las filas llevan estado de
 * pendiente-de-sync para que el {@link com.wattwise.android.sync.SyncWorker}
 * pueda enviarlas con estrategia «última escritura gana».
 */
@Dao
public interface ApplianceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ApplianceEntity appliance);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<ApplianceEntity> appliances);

    @Query("SELECT * FROM appliances WHERE isDeleted = 0 ORDER BY updatedAt DESC, localId DESC")
    List<ApplianceEntity> getAllActive();

    @Query("SELECT * FROM appliances WHERE isPendingSync = 1 ORDER BY updatedAt ASC")
    List<ApplianceEntity> getPending();

    @Query("SELECT * FROM appliances WHERE serverId = :serverId LIMIT 1")
    ApplianceEntity getByServerId(long serverId);

    @Query("SELECT * FROM appliances WHERE localId = :localId LIMIT 1")
    ApplianceEntity getByLocalId(long localId);

    /** Marca una fila como limpia tras un round-trip correcto con el servidor. */
    @Query("UPDATE appliances SET serverId = :serverId, isPendingSync = 0, isDeleted = 0, updatedAt = :now WHERE localId = :localId")
    int markSynced(long localId, long serverId, long now);

    /** Marca una fila para un futuro DELETE en el servidor. */
    @Query("UPDATE appliances SET isDeleted = 1, isPendingSync = 1, updatedAt = :now WHERE localId = :localId")
    int markDeleted(long localId, long now);

    @Query("DELETE FROM appliances WHERE localId = :localId")
    int deleteRow(long localId);

    /** Sobrescribe la contabilidad pendiente para adopción directa del servidor. */
    @Query("UPDATE appliances SET isPendingSync = 0, isDeleted = 0 WHERE serverId = :serverId")
    int clearPending(long serverId);
}