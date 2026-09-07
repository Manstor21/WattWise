package com.wattwise.android.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.wattwise.android.data.local.entity.ApplianceEntity;
import com.wattwise.android.data.local.entity.PriceRecordEntity;
import com.wattwise.android.data.local.entity.RecommendationEntity;

/**
 * Room database. Version 1 is the launch schema.
 *
 * <p>Migration strategy: every schema change increments {@code version} and adds
 * a {@code Migration} to {@link #MIGRATIONS} (an empty array today). Room runs
 * them in order; never bump the version without shipping a migration, otherwise
 * users lose their offline cache. {@code exportSchema=true} keeps the JSON schema
 * under {@code app/schemas} for the migration test we plan to add later.
 */
@Database(
        entities = {PriceRecordEntity.class, ApplianceEntity.class, RecommendationEntity.class},
        version = 1,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class WattWiseDatabase extends RoomDatabase {

    private static final String DB_NAME = "wattwise.db";

    /** Ordered list of migrations; empty on the launch schema. */
    @SuppressWarnings("unused")
    private static final androidx.room.migration.Migration[] MIGRATIONS = new androidx.room.migration.Migration[0];

    private static volatile WattWiseDatabase sInstance;

    public abstract PriceDao priceDao();

    public abstract ApplianceDao applianceDao();

    public abstract RecommendationDao recommendationDao();

    public static WattWiseDatabase get(Context context) {
        if (sInstance == null) {
            synchronized (WattWiseDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    WattWiseDatabase.class,
                                    DB_NAME)
                            .addMigrations(MIGRATIONS)
                            // The app is deliberately offline-first: stale local data
                            // beats a crash loop when the API is down.
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return sInstance;
    }
}