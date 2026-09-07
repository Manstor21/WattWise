package com.wattwise.android.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * A user appliance with offline-editing bookkeeping:
 * <ul>
 *   <li>{@link #isPendingSync} — local changes not yet pushed to the server.</li>
 *   <li>{@link #isDeleted}   — tombstone; the SyncWorker sends a server DELETE.</li>
 *   <li>{@link #updatedAt}   — epoch millis of the last local mutation, the
 *       local half of the last-write-wins key in {@link
 *       com.wattwise.android.sync.ConflictResolver}.</li>
 * </ul>
 */
@Entity(tableName = "appliances")
public class ApplianceEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    /** Null until the row has been created on the server. */
    public Long serverId;

    public String name;
    public String type; // WASHING_MACHINE | DISHWASHER | EV_CHARGER | DRYER | POOL_PUMP | AC | OTHER
    public Integer powerWatts;
    public BigDecimal avgCycleKwh;
    public Integer estimatedCycleMinutes;
    public boolean isActive;

    public boolean isPendingSync;
    public boolean isDeleted;
    public long updatedAt;
}