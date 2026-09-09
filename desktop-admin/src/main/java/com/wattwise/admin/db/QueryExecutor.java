package com.wattwise.admin.db;

import com.wattwise.admin.model.PriceRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consultas JDBC reutilizables. Todas las consultas usan parámetros {@link PreparedStatement}
 * (superficie de inyección SQL nula). Los upserts, búsquedas y actualizaciones de precios se
 * mantienen aquí para que las capas de servicio y de IU permanezcan delgadas.
 *
 * <p>Notas entre dialectos:
 * <ul>
 *   <li>El upsert de {@code price_records} se basa en la clave {@code timestamp} (UNIQUE).
 *       SQLite usa {@code INSERT ... ON CONFLICT(timestamp) DO UPDATE}; SQL Server usa
 *       {@code MERGE}.</li>
 *   <li>Los timestamps y las fechas viajan como cadenas ISO-8601 UTC de ancho fijo
 *       ({@code 2025-06-16T08:00:00Z}, {@code 2025-06-16}), que se comparan correctamente tanto
 *       en SQLite (TEXT) como en SQL Server (conversión implícita DATETIME2/DATE).</li>
 *   <li>El override de color de semáforo opcional se lee de {@code price_color_override} mediante
 *       un LEFT JOIN; las escrituras recurren a UPDATE-then-INSERT, por lo que no se necesita SQL
 *       específico de cada dialecto.</li>
 * </ul>
 */
public final class QueryExecutor {

    public static final String PRICE_OVERRIDE_TABLE = "price_color_override";

    private static final String PRICE_SELECT = """
            SELECT p.id, p.timestamp, p.price_eur_per_kwh, p.plus_tax_eur_per_kwh,
                   p.total_eur_per_kwh, p.source, p.date, c.color
            FROM price_records p
            LEFT JOIN price_color_override c ON c.price_record_id = p.id
            """;

    private static final int SCALE = 6;

    private QueryExecutor() {
    }

    // ---------------------------------------------------------------- consultas

    /**
     * Carga registros de precios, opcionalmente limitados a un rango de fechas UTC (ambos
     * extremos inclusive).
     */
    public static List<PriceRecord> findPrices(Connection c, LocalDate from, LocalDate to)
            throws SQLException {
        StringBuilder sql = new StringBuilder(PRICE_SELECT);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (from != null) {
            conditions.add("p.timestamp >= ?");
            params.add(isoUtc(from.atStartOfDay()));
        }
        if (to != null) {
            conditions.add("p.timestamp < ?");
            params.add(isoUtc(to.plusDays(1).atStartOfDay()));
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY p.timestamp");

        List<PriceRecord> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapPriceRow(rs));
                }
            }
        }
        return rows;
    }

    public static List<PriceRecord> findPrices(Connection c) throws SQLException {
        return findPrices(c, null, null);
    }

    public static boolean existsTimestamp(Connection c, OffsetDateTime timestamp) throws SQLException {
        Objects.requireNonNull(timestamp, "timestamp");
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM price_records WHERE timestamp = ?")) {
            ps.setString(1, isoUtc(timestamp));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static PriceRecord findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(PRICE_SELECT + " WHERE p.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapPriceRow(rs) : null;
            }
        }
    }

    // ---------------------------------------------------------------- DML

    /**
     * Hace upsert de un registro de precio con clave en el {@code timestamp} UNIQUE. Devuelve el
     * id persistido. Los overrides de color se escriben mediante {@link #upsertColorOverride}.
     */
    public static long upsertPrice(Connection c, PriceRecord record) throws SQLException {
        OffsetDateTime ts = Objects.requireNonNull(record.getTimestamp(), "timestamp");
        boolean sqlite = DatabaseConnection.isSqlite(c);
        String sql = sqlite ? SQLITE_UPSERT : MSSQL_MERGE;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (sqlite) {
                bindPriceRow(ps, 1, record);
                ps.executeUpdate();
            } else {
                bindPriceRow(ps, 1, record);
                bindPriceRow(ps, 7, record);
                ps.executeUpdate();
            }
        }

        long id = findIdByTimestamp(c, ts);
        upsertColorOverride(c, id, record.getColor());
        return id;
    }

    /** Corrige las columnas de precio de un registro existente; lo usa el editor en línea de precios. */
    public static int updatePrice(Connection c, PriceRecord record) throws SQLException {
        String sql = "UPDATE price_records SET price_eur_per_kwh = ?, plus_tax_eur_per_kwh = ?, "
                + "total_eur_per_kwh = ?, source = ? WHERE id = ?";
        int updated;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scale6(record.getPrice()).toPlainString());
            if (record.getPlusTax() == null) {
                ps.setNull(2, java.sql.Types.NUMERIC);
            } else {
                ps.setString(2, scale6(record.getPlusTax()).toPlainString());
            }
            ps.setString(3, scale6(record.getTotal()).toPlainString());
            ps.setString(4, record.getSourceOrDefault());
            ps.setLong(5, record.getId());
            updated = ps.executeUpdate();
        }
        if (updated > 0) {
            upsertColorOverride(c, record.getId(), record.getColor());
        }
        return updated;
    }

    /**
     * Persiste el color opcional de semáforo. Un color vacío limpia el override para que vuelva a
     * aplicar el clasificador de semáforo del backend. Usa UPDATE-then-INSERT para no requerir
     * sintaxis de upsert específica de cada dialecto.
     */
    public static void upsertColorOverride(Connection c, long priceRecordId, String color)
            throws SQLException {
        boolean tableExists = hasTable(c, PRICE_OVERRIDE_TABLE);
        if (color == null || color.isBlank()) {
            if (tableExists) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM " + PRICE_OVERRIDE_TABLE + " WHERE price_record_id = ?")) {
                    ps.setLong(1, priceRecordId);
                    ps.executeUpdate();
                }
            }
            return;
        }
        try {
            if (tableExists) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + PRICE_OVERRIDE_TABLE + " SET color = ? WHERE price_record_id = ?")) {
                    ps.setString(1, color);
                    ps.setLong(2, priceRecordId);
                    if (ps.executeUpdate() == 0) {
                        try (PreparedStatement ins = c.prepareStatement(
                                "INSERT INTO " + PRICE_OVERRIDE_TABLE
                                        + " (price_record_id, color, updated_at) VALUES (?, ?, ?)")) {
                            ins.setLong(1, priceRecordId);
                            ins.setString(2, color);
                            ins.setString(3, Instant.now().toString());
                            ins.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            // Los overrides de color son una comodidad local; una tabla ausente/limitada por
            // permisos no debe bloquear la corrección de precios.
            System.err.println("Advertencia: no se pudo guardar el color del precio " + priceRecordId + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- usuarios

    public record UserRow(long id, String username, String email, String role,
                          String createdAt, Boolean isActive) {
    }

    public static boolean supportsUserActiveToggle(Connection c) throws SQLException {
        return hasColumn(c, "users", "is_active");
    }

    public static List<UserRow> findUsers(Connection c) throws SQLException {
        boolean active = supportsUserActiveToggle(c);
        String sql = active
            ? "SELECT id, username, email, role, created_at, is_active FROM users ORDER BY id"
            : "SELECT id, username, email, role, created_at FROM users ORDER BY id";
        List<UserRow> users = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Boolean isActive = active ? rs.getInt("is_active") != 0 : null;
                users.add(new UserRow(rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("role"),
                        rs.getString("created_at"),
                        isActive));
            }
        }
        return users;
    }

    /**
     * Activa/desactiva un usuario demo ({@code is_active}) cuando la columna existe. El esquema
     * real del backend (V1__init.sql) no tiene indicador de activación; en SQL Server es un no-op
     * que devuelve 0.
     */
    public static int setUserActive(Connection c, long userId, boolean active) throws SQLException {
        if (!supportsUserActiveToggle(c)) {
            return 0;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET is_active = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setString(2, Instant.now().toString());
            ps.setLong(3, userId);
            return ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- metadatos

    public static boolean hasTable(Connection c, String table) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, "%", null)) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, table, null)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean test(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    // ---------------------------------------------------------------- utilidades

    private static long findIdByTimestamp(Connection c, OffsetDateTime timestamp) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM price_records WHERE timestamp = ?")) {
            ps.setString(1, isoUtc(timestamp));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("No se encontró el registro de precio con timestamp " + timestamp);
    }

    private static PriceRecord mapPriceRow(ResultSet rs) throws SQLException {
        OffsetDateTime ts = readInstant(rs, "timestamp");
        LocalDate date = readDate(rs, "date");
        return new PriceRecord(
                rs.getLong("id"),
                ts,
                decimal(rs, "price_eur_per_kwh"),
                decimal(rs, "plus_tax_eur_per_kwh"),
                decimal(rs, "total_eur_per_kwh"),
                rs.getString("source"),
                rs.getString("color"));
    }

    private static BigDecimal decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setString(i + 1, (String) params.get(i));
        }
    }

    private static void bindPriceRow(PreparedStatement ps, int start, PriceRecord record)
            throws SQLException {
        ps.setString(start, isoUtc(record.getTimestamp()));
        ps.setString(start + 1, scale6(record.getPrice()).toPlainString());
        if (record.getPlusTax() == null) {
            ps.setNull(start + 2, java.sql.Types.NUMERIC);
        } else {
            ps.setString(start + 2, scale6(record.getPlusTax()).toPlainString());
        }
        ps.setString(start + 3, scale6(record.getTotal()).toPlainString());
        ps.setString(start + 4, record.getSourceOrDefault());
        ps.setString(start + 5, record.getDate().toString());
    }

    private static OffsetDateTime readInstant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC).atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private static LocalDate readDate(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static BigDecimal scale6(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String isoUtc(LocalDateTime dateTime) {
        return dateTime.toInstant(ZoneOffset.UTC).toString();
    }

    private static String isoUtc(OffsetDateTime timestamp) {
        return DatabaseConnection.isoUtc(timestamp);
    }

    // ---------------------------------------------------------------- SQL por dialecto

    private static final String SQLITE_UPSERT = """
            INSERT INTO price_records (timestamp, price_eur_per_kwh, plus_tax_eur_per_kwh,
                                       total_eur_per_kwh, source, date)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(timestamp) DO UPDATE SET
                price_eur_per_kwh    = excluded.price_eur_per_kwh,
                plus_tax_eur_per_kwh = excluded.plus_tax_eur_per_kwh,
                total_eur_per_kwh    = excluded.total_eur_per_kwh,
                source               = excluded.source,
                date                 = excluded.date
            """;

    private static final String MSSQL_MERGE = """
            MERGE INTO price_records WITH (HOLDLOCK) AS t
            USING (SELECT ? AS ts) AS s
            ON t.timestamp = s.ts
            WHEN MATCHED THEN UPDATE SET
                price_eur_per_kwh    = ?,
                plus_tax_eur_per_kwh = ?,
                total_eur_per_kwh    = ?,
                source               = ?,
                date                 = ?
            WHEN NOT MATCHED THEN INSERT (timestamp, price_eur_per_kwh, plus_tax_eur_per_kwh,
                                          total_eur_per_kwh, source, date)
            VALUES (?, ?, ?, ?, ?, ?);
            """;
}