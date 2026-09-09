package com.wattwise.admin.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Random;

/**
 * Gestiona la conexión JDBC única usada por la herramienta de administración de escritorio.
 *
 * <p>Se admiten dos modos:
 * <ul>
 *   <li>{@link Mode#SQLITE SQLITE} - base de datos local de demostración. Si el archivo aún no
 *       existe, se crea e inicializa con {@code /db/demo-init.sql} junto con datos de ejemplo
 *       deterministas.</li>
 *   <li>{@link Mode#SQLSERVER SQLSERVER} - SQL Server de producción mediante una URL JDBC. La
 *       contraseña se mantiene solo en memoria y nunca se persiste en disco.</li>
 * </ul>
 *
 * <p>No seguro por diseño en entornos multihilo: todo el trabajo de la IU se serializa a través
 * de {@code SwingWorker}, por lo que los paneles usan esta conexión de forma secuencial.
 */
public final class DatabaseConnection implements AutoCloseable {

    public static final String DEFAULT_SQLITE_FILE = "wattwise-admin-demo.db";
    private static final String DEMO_INIT_SCRIPT = "/db/demo-init.sql";

    public enum Mode {
        SQLITE,
        SQLSERVER
    }

    private final Mode mode;
    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    private DatabaseConnection(Mode mode, String url, String user, String password) {
        this.mode = mode;
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /**
     * Abre (creándola si es necesario) una base de datos de demostración SQLite. Los archivos
     * inexistentes se inicializan con el esquema demo y datos de ejemplo deterministas.
     */
    public static DatabaseConnection connectSqlite(Path dbFile) throws SQLException, IOException {
        Path absolute = dbFile.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String jdbcUrl = "jdbc:sqlite:" + absolute.toString().replace('\\', '/');
        DatabaseConnection dbc = new DatabaseConnection(Mode.SQLITE, jdbcUrl, null, null);
        try {
            dbc.open();
            dbc.initialiseDemoSchema();
            return dbc;
        } catch (SQLException | IOException e) {
            dbc.closeQuietly();
            throw e;
        }
    }

    /**
     * Abre una conexión de producción a SQL Server. La contraseña se mantiene solo en memoria.
     */
    public static DatabaseConnection connectSqlServer(String jdbcUrl, String user, String password)
            throws SQLException {
        if (jdbcUrl == null || !jdbcUrl.toLowerCase().startsWith("jdbc:sqlserver:")) {
            throw new SQLException("La URL JDBC debe empezar por jdbc:sqlserver://");
        }
        DatabaseConnection dbc = new DatabaseConnection(Mode.SQLSERVER, jdbcUrl, user, password);
        try {
            dbc.open();
            return dbc;
        } catch (SQLException e) {
            dbc.closeQuietly();
            throw e;
        }
    }

    private void open() throws SQLException {
        // Ambos drivers se registran vía ServiceLoader; no se necesita Class.forName.
        connection = user == null || user.isBlank()
            ? DriverManager.getConnection(url)
            : DriverManager.getConnection(url, user, password);
    }

    public boolean testConnection() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    /** Devuelve la conexión JDBC activa. Nunca se expone fuera de la capa {@code db}/{@code service}. */
    public Connection get() {
        return connection;
    }

    public boolean isSqlite() {
        return mode == Mode.SQLITE;
    }

    /** Detecta el dialecto SQLite en tiempo de ejecución (útil para conexiones desechables en tests). */
    public static boolean isSqlite(Connection c) throws SQLException {
        String product = c.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase().contains("sqlite");
    }

    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // mejor esfuerzo al cerrar
            }
            connection = null;
        }
    }

    /**
     * Garantiza que existan las tablas demo y siembra datos de ejemplo deterministas en una base
     * de datos nueva.
     */
    private void initialiseDemoSchema() throws SQLException, IOException {
        if (!isSqlite()) {
            throw new IllegalStateException("Demo schema initialisation is SQLite-only.");
        }
        executeScript(connection, DEMO_INIT_SCRIPT);
        if (isTableEmpty(connection, "users")) {
            seedDemoUsers(connection);
        }
        if (isTableEmpty(connection, "price_records")) {
            seedDemoPrices(connection);
        }
        if (isTableEmpty(connection, "job_log")) {
            seedDemoJobLog(connection);
        }
    }

    /**
     * Ejecuta un script SQL cuyas sentencias se separan por {@code ;}. Las líneas que empiezan
     * por {@code --} y las líneas en blanco se omiten. Se admiten sentencias multilínea.
     *
     * @param resourcePath recurso de classpath, p. ej. {@code /db/demo-init.sql}
     */
    public static void executeScript(Connection c, String resourcePath) throws SQLException, IOException {
        try (InputStream in = DatabaseConnection.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("SQL script not found on classpath: " + resourcePath);
            }
            StringBuilder statement = new StringBuilder();
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                        continue;
                    }
                    if (trimmed.endsWith(";")) {
                        statement.append(line, 0, line.length() - 1);
                        executeStatement(c, statement.toString().strip());
                        statement.setLength(0);
                    } else {
                        statement.append(line).append('\n');
                    }
                }
                if (!statement.toString().isBlank()) {
                    executeStatement(c, statement.toString().strip());
                }
            }
        }
    }

    private static void executeStatement(Connection c, String sql) throws SQLException {
        if (sql.isBlank()) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    public static boolean isTableEmpty(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getLong(1) == 0;
        }
    }

    /**
     * Datos de ejemplo deterministas para demos locales: 3 usuarios, ~2 días de precios horarios
     * sintéticos y un log de jobs pequeño. Los valores se extraen de un {@link Random} con semilla
     * fija para que toda base de datos demo sea idéntica, lo que mantiene estables los tests.
     */
    public static void seedDemoUsers(Connection c) throws SQLException {
        String sql = "INSERT INTO users (username, email, password, role, is_active) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            insertUser(ps, "admin", "admin@wattwise.local", "DEMO-ONLY-HASH", "ADMIN", 1);
            insertUser(ps, "operador", "operador@wattwise.local", "DEMO-ONLY-HASH", "USER", 1);
            insertUser(ps, "analista", "analista@wattwise.local", "DEMO-ONLY-HASH", "USER", 0);
            ps.executeBatch();
        }
    }

    private static void insertUser(PreparedStatement ps, String username, String email,
                                   String password, String role, int active) throws SQLException {
        ps.setString(1, username);
        ps.setString(2, email);
        ps.setString(3, password);
        ps.setString(4, role);
        ps.setInt(5, active);
        ps.addBatch();
    }

    public static void seedDemoPrices(Connection c) throws SQLException {
        String sql = "INSERT INTO price_records "
                + "(timestamp, price_eur_per_kwh, plus_tax_eur_per_kwh, total_eur_per_kwh, source, date) "
                + "VALUES (?,?,?,?,?,?)";
        Random random = new Random(42L);
        LocalDate startDay = LocalDate.now(ZoneOffset.UTC).minusDays(2);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int day = 0; day < 2; day++) {
                for (int hour = 0; hour < 24; hour++) {
                    OffsetDateTime ts = startDay.plusDays(day).atTime(hour, 0).atOffset(ZoneOffset.UTC);
                    double wave = basePriceWave(hour);
                    double price = Math.max(0.03, wave + (random.nextDouble() - 0.5) * 0.03);
                    BigDecimal priceBd = round6(price);
                    BigDecimal taxBd = round6(price * 0.10 + 0.021);
                    BigDecimal totalBd = priceBd.add(taxBd).setScale(6, RoundingMode.HALF_UP);
                    boolean manual = hour == 12 || hour == 20;
                    ps.setString(1, isoUtc(ts));
                    ps.setString(2, priceBd.toPlainString());
                    ps.setString(3, taxBd.toPlainString());
                    ps.setString(4, totalBd.toPlainString());
                    ps.setString(5, manual ? "MANUAL" : "ESIOS");
                    ps.setString(6, ts.toLocalDate().toString());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private static double basePriceWave(int hour) {
        if (hour < 8) {
            return 0.10;
        }
        if (hour < 14) {
            return 0.19;
        }
        if (hour < 18) {
            return 0.14;
        }
        if (hour < 22) {
            return 0.21;
        }
        return 0.11;
    }

    public static void seedDemoJobLog(Connection c) throws SQLException {
        String sql = "INSERT INTO job_log (job_name, status, started_at, finished_at, message, rows_affected) "
                + "VALUES (?,?,?,?,?,?)";
        String now = Instant.now().toString();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "ESIOS_FETCH");
            ps.setString(2, "SUCCESS");
            ps.setString(3, LocalDate.now().atTime(6, 0).toInstant(ZoneOffset.UTC).toString());
            ps.setString(4, LocalDate.now().atTime(6, 0, 12).toInstant(ZoneOffset.UTC).toString());
            ps.setString(5, "Importación ESIOS completada");
            ps.setInt(6, 48);
            ps.addBatch();
            ps.executeBatch();
        }
    }

    public static BigDecimal round6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    /** Normaliza un offset date-time a una cadena ISO-8601 UTC de ancho fijo (p. ej. {@code 2025-06-16T08:00:00Z}). */
    public static String isoUtc(OffsetDateTime timestamp) {
        return timestamp.toInstant().toString();
    }
}