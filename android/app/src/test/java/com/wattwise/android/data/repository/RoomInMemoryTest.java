package com.wattwise.android.data.repository;

/*
 * Room in-memory tests require either:
 *   - Robolectric (annotate class with @RunWith(RobolectricTestRunner.class)),
 *   - or Android instrumentation tests (src/androidTest/).
 *
 * The project intentionally avoids a Robolectric dependency to keep the test
 * classpath JVM-only and fast. Repository logic that depends on Room is
 * therefore tested through the app's manual / instrumented QA; pure logic
 * (ConflictResolver, OptimalWindowScheduler) lives in sibling JVM tests.
 *
 * When a CI instrumented test run is added, the following skeleton can be
 * uncommented and moved to src/androidTest/ with appropriate Gradle
 * dependencies (androidTestImplementation 'androidx.room:room-testing:2.6.1').
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