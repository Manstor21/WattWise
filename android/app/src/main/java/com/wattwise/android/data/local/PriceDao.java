package com.wattwise.android.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.wattwise.android.data.local.entity.PriceRecordEntity;

import java.util.List;

/**
 * Reads/writes cached price slots. The cache intentionally keeps roughly three
 * days of slots so the "HOY / MAÑANA" tabs and the offline dashboard always have
 * data to render.
 */
@Dao
public interface PriceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PriceRecordEntity price);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<PriceRecordEntity> prices);

    @Query("SELECT * FROM price_records ORDER BY timestamp ASC")
    List<PriceRecordEntity> getAll();

    /** Slots inside an inclusive UTC ISO range {@code [fromIso, toIso]}. */
    @Query("SELECT * FROM price_records WHERE timestamp >= :fromIso AND timestamp <= :toIso ORDER BY timestamp ASC")
    List<PriceRecordEntity> getBetween(String fromIso, String toIso);

    @Query("DELETE FROM price_records WHERE timestamp < :cutoffIso")
    int deleteOlderThan(String cutoffIso);

    @Query("SELECT COUNT(*) FROM price_records")
    int count();
}