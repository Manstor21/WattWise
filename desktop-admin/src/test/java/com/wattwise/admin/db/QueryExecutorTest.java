package com.wattwise.admin.db;

import com.wattwise.admin.model.PriceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link QueryExecutor} against a real SQLite database. In-memory SQLite
 * ({@code jdbc:sqlite::memory:}) lives for as long as the single connection is open, so every
 * test opens its own connection with the full demo schema+seed and closes it afterwards.
 */
class QueryExecutorTest {

    private static final OffsetDateTime TS = OffsetDateTime.parse("2030-05-01T08:00:00Z");

    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        DatabaseConnection.executeScript(connection, "/db/demo-init.sql");
        DatabaseConnection.seedDemoUsers(connection);
        DatabaseConnection.seedDemoPrices(connection);
        DatabaseConnection.seedDemoJobLog(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    private PriceRecord newRecord(OffsetDateTime ts, String price, String tax, String total, String source, String color) {
        return new PriceRecord(0L, ts, new BigDecimal(price),
                tax == null ? null : new BigDecimal(tax), new BigDecimal(total), source, color);
    }

    @Test
    void upsertInsertsNewTimestampOnce() throws SQLException {
        QueryExecutor.upsertPrice(connection, newRecord(TS, "0.10", "0.02", "0.12", "ESIOS", "GREEN"));

        List<PriceRecord> found = QueryExecutor.findPrices(connection);
        PriceRecord inserted = found.stream().filter(r -> r.getTimestamp().equals(TS)).findFirst().orElseThrow();
        assertEquals(0, inserted.getPrice().compareTo(new BigDecimal("0.100000")));
        assertEquals("GREEN", inserted.getColor());
        assertTrue(inserted.getId() > 0, "auto-generated id must be returned");
    }

    @Test
    void upsertUpdatesExistingTimestampInsteadOfDuplicating() throws SQLException {
        QueryExecutor.upsertPrice(connection, newRecord(TS, "0.10", "0.02", "0.12", "ESIOS", "GREEN"));
        QueryExecutor.upsertPrice(connection, newRecord(TS, "0.42", "0.05", "0.47", "MANUAL", "RED"));

        List<PriceRecord> all = QueryExecutor.findPrices(connection, null, null);
        long matches = all.stream().filter(r -> r.getTimestamp().equals(TS)).count();
        assertEquals(1, matches, "UNIQUE(timestamp) must not create duplicates");
        all.stream().filter(r -> r.getTimestamp().equals(TS)).forEach(r -> {
            assertEquals(0, r.getPrice().compareTo(new BigDecimal("0.420000")));
            assertEquals("MANUAL", r.getSource());
            assertEquals("RED", r.getColor());
        });
    }

    @Test
    void updatePriceCorrectionPersistsAndWritesColorOverride() throws SQLException {
        long id = QueryExecutor.upsertPrice(connection, newRecord(TS, "0.10", "0.02", "0.12", "ESIOS", null));

        PriceRecord correction = newRecord(TS, "0.31", "0.03", "0.34", "ESIOS", "AMBER");
        correction.setId(id);
        int updated = QueryExecutor.updatePrice(connection, correction);
        assertEquals(1, updated);

        PriceRecord reloaded = QueryExecutor.findById(connection, id);
        assertNotNull(reloaded);
        assertEquals(0, reloaded.getPrice().compareTo(new BigDecimal("0.310000")));
        assertEquals(0, reloaded.getTotal().compareTo(new BigDecimal("0.340000")));
        assertEquals("AMBER", reloaded.getColor(), "colour override must be readable back");
    }

    @Test
    void clearingColorOverrideDeletesOverride() throws SQLException {
        long id = QueryExecutor.upsertPrice(connection, newRecord(TS, "0.10", "0.02", "0.12", "ESIOS", "GREEN"));

        PriceRecord cleared = newRecord(TS, "0.10", "0.02", "0.12", "ESIOS", null);
        cleared.setId(id);
        QueryExecutor.updatePrice(connection, cleared);

        assertTrue(!QueryExecutor.findById(connection, id).hasColorOverride());
    }

    @Test
    void rangeFilterReturnsOnlySlotsInsideRange() throws SQLException {
        QueryExecutor.upsertPrice(connection, newRecord(TS, "0.10", null, "0.10", "ESIOS", null));
        QueryExecutor.upsertPrice(connection, newRecord(TS.plusDays(1), "0.11", null, "0.11", "ESIOS", null));
        QueryExecutor.upsertPrice(connection, newRecord(TS.plusDays(2), "0.12", null, "0.12", "ESIOS", null));

        LocalDate day1 = TS.toLocalDate();
        List<PriceRecord> oneDay = QueryExecutor.findPrices(connection, day1, day1);
        assertEquals(1, oneDay.size());
        assertEquals(TS.toInstant(), oneDay.get(0).getTimestamp().toInstant());

        List<PriceRecord> twoDays = QueryExecutor.findPrices(connection, day1, day1.plusDays(1));
        assertEquals(2, twoDays.size());

        List<PriceRecord> none = QueryExecutor.findPrices(connection, day1.plusDays(5), day1.plusDays(6));
        assertTrue(none.isEmpty());
    }

    @Test
    void userToggleUpdatesIsActive() throws SQLException {
        assertTrue(QueryExecutor.supportsUserActiveToggle(connection), "demo schema must expose is_active");

        var users = QueryExecutor.findUsers(connection);
        long targetId = users.stream().filter(u -> u.username().equals("admin"))
                .findFirst().orElseThrow().id();

        QueryExecutor.setUserActive(connection, targetId, false);
        var after = QueryExecutor.findUsers(connection);
        var admin = after.stream().filter(u -> u.id() == targetId).findFirst().orElseThrow();
        assertTrue(!admin.isActive());

        QueryExecutor.setUserActive(connection, targetId, true);
        admin = QueryExecutor.findUsers(connection).stream().filter(u -> u.id() == targetId)
                .findFirst().orElseThrow();
        assertTrue(admin.isActive());
    }

    @Test
    void demoSeedProducesDeterministicRecords() throws SQLException {
        var users = QueryExecutor.findUsers(connection);
        assertEquals(3, users.size(), "three demo users");
        users.forEach(u -> assertNotNull(u.email()));

        List<PriceRecord> prices = QueryExecutor.findPrices(connection, null, null);
        assertEquals(48, prices.size(), "48 hourly demo slots (~2 days)");
        long esios = prices.stream().filter(r -> "ESIOS".equals(r.getSource())).count();
        long manual = prices.stream().filter(r -> "MANUAL".equals(r.getSource())).count();
        assertEquals(48, esios + manual);
        assertTrue(manual > 0, "demo data includes MANUAL rows");

        assertTrue(QueryExecutor.hasTable(connection, "job_log"));
        assertTrue(QueryExecutor.hasTable(connection, "price_color_override"));
        assertTrue(QueryExecutor.hasColumn(connection, "users", "is_active"));
    }

    @Test
    void tmpFileBasedDatabaseWorks() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("wattwise-test");
        java.nio.file.Path db = dir.resolve("test.db");
        try {
            DatabaseConnection demo = DatabaseConnection.connectSqlite(db.toFile().toPath());
            assertTrue(QueryExecutor.test(demo.get()));
            assertEquals(48, QueryExecutor.findPrices(demo.get()).size());
            demo.close();
        } finally {
            java.nio.file.Files.deleteIfExists(db);
            java.nio.file.Files.deleteIfExists(dir);
        }
    }
}