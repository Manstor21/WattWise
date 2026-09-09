package com.wattwise.android.data.repository;

/*
 * Los tests de Room en memoria requieren o bien:
 *   - Robolectric (anotar la clase con @RunWith(RobolectricTestRunner.class)),
 *   - o tests de instrumentación de Android (src/androidTest/).
 *
 * El proyecto evita a propósito la dependencia de Robolectric para mantener el
 * classpath de tests solo JVM y rápido. La lógica de repositorio que depende de Room
 * se prueba por tanto mediante el QA manual / instrumentado de la app; la lógica pura
 * (ConflictResolver, OptimalWindowScheduler) vive en tests JVM hermanos.
 *
 * Cuando se añada una ejecución de tests instrumentados en CI, el siguiente esqueleto
 * puede descomentarse y moverse a src/androidTest/ con las dependencias de Gradle
 * adecuadas (androidTestImplementation 'androidx.room:room-testing:2.6.1').
 *
import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.wattwise.android.data.local.WattWiseDatabase;
import com.wattwise.android.data.local.ApplianceDao;
import com.wattwise.android.data.local.entity.ApplianceEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ApplianceRepositoryTest {

    private WattWiseDatabase db;
    private ApplianceDao dao;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, WattWiseDatabase.class)
                .allowMainThreadQueries().build();
        dao = db.applianceDao();
    }

    @After
    public void tearDown() { db.close(); }

    @Test
    public void insert_and_readBack() {
        ApplianceEntity e = new ApplianceEntity();
        e.name = "Lavadora";
        e.type = "WASHING_MACHINE";
        e.isPendingSync = true;
        e.updatedAt = System.currentTimeMillis();
        long id = dao.insert(e);

        List<ApplianceEntity> all = dao.getAllActive();
        assertEquals(1, all.size());
        assertEquals("Lavadora", all.get(0).name);
        assertTrue(all.get(0).isPendingSync);
    }
}
 */