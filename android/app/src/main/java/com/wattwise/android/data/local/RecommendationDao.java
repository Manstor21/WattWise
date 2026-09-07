package com.wattwise.android.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.wattwise.android.data.local.entity.RecommendationEntity;

import java.util.List;

/**
 * Cache for server-side recommendations (one per appliance).
 */
@Dao
public interface RecommendationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long[] insertAll(List<RecommendationEntity> recommendations);

    @Query("SELECT * FROM recommendations ORDER BY applianceName ASC")
    List<RecommendationEntity> getAll();

    @Query("SELECT * FROM recommendations WHERE applianceId = :applianceId LIMIT 1")
    RecommendationEntity getByApplianceId(long applianceId);

    @Query("DELETE FROM recommendations")
    int deleteAll();

    @Query("DELETE FROM recommendations WHERE applianceId = :applianceId")
    int deleteByApplianceId(long applianceId);
}