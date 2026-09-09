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
 * Base de datos Room. La versión 1 es el esquema de lanzamiento.
 *
 * <p>Estrategia de migración: cada cambio de esquema incrementa {@code version}
 * y añade una {@code Migration} a {@link #MIGRATIONS} (hoy un array vacío).
 * Room las ejecuta en orden; nunca subir la versión sin enviar una migración,
 * o los usuarios perderán su caché sin conexión. {@code exportSchema=true}
 * mantiene el esquema JSON bajo {@code app/schemas} para la prueba de
 * migración que planeamos añadir más adelante.
 */
@Database(
        entities = {PriceRecordEntity.class, ApplianceEntity.class, RecommendationEntity.class},
        version = 1,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class WattWiseDatabase extends RoomDatabase {

    private static final String DB_NAME = "wattwise.db";

    /** Lista ordenada de migraciones; vacía en el esquema de lanzamiento. */
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
                            // La app es deliberadamente offline-first: unos datos
                            // locales obsoletos son preferibles a un bucle de
                            // cierres cuando la API no está disponible.
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return sInstance;
    }
}