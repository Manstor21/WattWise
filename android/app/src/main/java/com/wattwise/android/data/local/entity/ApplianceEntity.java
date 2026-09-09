package com.wattwise.android.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Un electrodoméstico del usuario con contabilidad de edición sin conexión:
 * <ul>
 *   <li>{@link #isPendingSync} — cambios locales aún no enviados al servidor.</li>
 *   <li>{@link #isDeleted}   — marca de borrado; el SyncWorker envía un DELETE al servidor.</li>
 *   <li>{@link #updatedAt}   — milisegundos epoch de la última mutación local, la
 *       mitad local de la clave «última escritura gana» en {@link
 *       com.wattwise.android.sync.ConflictResolver}.</li>
 * </ul>
 */
@Entity(tableName = "appliances")
public class ApplianceEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    /** Nulo hasta que la fila se ha creado en el servidor. */
    public Long serverId;

    public String name;
    public String type; // Valores posibles: WASHING_MACHINE | DISHWASHER | EV_CHARGER | DRYER | POOL_PUMP | AC | OTHER
    public Integer powerWatts;
    public BigDecimal avgCycleKwh;
    public Integer estimatedCycleMinutes;
    public boolean isActive;

    public boolean isPendingSync;
    public boolean isDeleted;
    public long updatedAt;
}