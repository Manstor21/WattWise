package com.wattwise.admin.service;

import com.wattwise.admin.db.QueryExecutor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads job execution logs and derives a data summary from {@code price_records}.
 *
 * <p>The backend schema has no {@code job_log} table (see V1__init.sql), so the table exists
 * only on the SQLite demo database. When it is absent the panel falls back to the
 * {@link #loadSummary} values derived directly from {@code price_records}.
 */
public final class LogViewerService {

    public record JobLogEntry(long id, String jobName, String status, String startedAt,
                              String finishedAt, String message, long rowsAffected) {
    }

    public record DataSummary(long totalRecords, OffsetDateTime lastTimestamp, BigDecimal avgTotal,
                              long overriddenColors, Map<String, Long> countsBySource) {
    }

    private LogViewerService() {
    }

    public static boolean hasJobLog(Connection connection) throws SQLException {
        return QueryExecutor.hasTable(connection, "job_log");
    }

    public static List<JobLogEntry> loadJobLogs(Connection connection) throws SQLException {
        List<JobLogEntry> entries = new ArrayList<>();
        String sql = "SELECT id, job_name, status, started_at, finished_at, message, rows_affected "
                + "FROM job_log ORDER BY started_at DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new JobLogEntry(
                        rs.getLong("id"),
                        rs.getString("job_name"),
                        rs.getString("status"),
                        rs.getString("started_at"),
                        rs.getString("finished_at"),
                        rs.getString("message"),
                        rs.getLong("rows_affected")));
            }
        }
        return entries;
    }

    /**
     * Summary of the price data: record count, most recent timestamp, average total price and
     * record counts grouped by source. Answers "is the data pipeline alive?" at a glance.
     */
    public static DataSummary loadSummary(Connection connection) throws SQLException {
        long totalRecords = 0;
        OffsetDateTime lastTimestamp = null;
        BigDecimal avgTotal = null;
        long overriddenColors = 0;
        Map<String, Long> bySource = new LinkedHashMap<>();

        String sql = "SELECT COUNT(*), MAX(timestamp), AVG(total_eur_per_kwh) FROM price_records";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                totalRecords = rs.getLong(1);
                String last = rs.getString(2);
                if (last != null) {
                    lastTimestamp = OffsetDateTime.parse(last);
                }
                BigDecimal avg = rs.getBigDecimal(3);
                if (avg != null) {
                    avgTotal = avg.setScale(6, RoundingMode.HALF_UP);
                }
            }
        }

        String sourceSql = "SELECT source, COUNT(*) FROM price_records GROUP BY source ORDER BY source";
        try (PreparedStatement ps = connection.prepareStatement(sourceSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                bySource.put(rs.getString(1), rs.getLong(2));
            }
        }

        if (QueryExecutor.hasTable(connection, QueryExecutor.PRICE_OVERRIDE_TABLE)) {
            String colorSql = "SELECT COUNT(*) FROM " + QueryExecutor.PRICE_OVERRIDE_TABLE;
            try (PreparedStatement ps = connection.prepareStatement(colorSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    overriddenColors = rs.getLong(1);
                }
            }
        }

        return new DataSummary(totalRecords, lastTimestamp, avgTotal, overriddenColors, bySource);
    }
}