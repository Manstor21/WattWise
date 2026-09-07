package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.service.LogViewerService;
import com.wattwise.admin.service.LogViewerService.DataSummary;
import com.wattwise.admin.service.LogViewerService.JobLogEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Job execution logs plus a data pipeline summary. When {@code job_log} is missing (e.g. the
 * production SQL Server schema, which has no such table) the panel shows a summary derived
 * directly from {@code price_records} with an explanatory notice.
 */
public final class JobLogPanel extends JPanel {

    private static final String[] LOG_COLUMNS = {
            "Job", "Estado", "Inicio (UTC)", "Fin (UTC)", "Filas", "Mensaje"
    };
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final DatabaseConnection db;
    private final MainFrame frame;

    private final JLabel recordsLabel = new JLabel("—");
    private final JLabel lastLabel = new JLabel("—");
    private final JLabel avgLabel = new JLabel("—");
    private final JLabel sourceLabel = new JLabel("—");
    private final JLabel overridesLabel = new JLabel("—");
    private final JLabel fallbackNote = new JLabel(" ");
    private final LogTableModel logModel = new LogTableModel();
    private final JTable logTable;

    public JobLogPanel(DatabaseConnection db, MainFrame frame) {
        this.db = db;
        this.frame = frame;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        add(buildSummaryPanel(), BorderLayout.NORTH);

        logTable = new JTable(logModel);
        logTable.setRowHeight(22);
        logTable.setAutoCreateRowSorter(true);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(420);
        add(new JScrollPane(logTable), BorderLayout.CENTER);
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Resumen de datos"));

        JPanel grid = new JPanel(new GridLayout(2, 4, 12, 4));
        grid.add(summaryCell("Registros", recordsLabel));
        grid.add(summaryCell("Último timestamp", lastLabel));
        grid.add(summaryCell("Total medio €/kWh", avgLabel));
        grid.add(summaryCell("Colores override", overridesLabel));
        grid.add(summaryCell("Por fuente", sourceLabel));

        fallbackNote.setForeground(new Color(150, 110, 0));
        JButton refresh = new JButton("Refrescar");
        refresh.addActionListener(e -> refresh());

        JPanel top = new JPanel(new BorderLayout());
        JPanel center = new JPanel(new BorderLayout());
        center.add(grid, BorderLayout.NORTH);
        center.add(fallbackNote, BorderLayout.SOUTH);
        top.add(center, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        return panel;
    }

    private JPanel summaryCell(String title, JLabel value) {
        JPanel cell = new JPanel(new BorderLayout());
        JLabel t = new JLabel(title);
        t.setForeground(Color.GRAY);
        cell.add(t, BorderLayout.NORTH);
        value.setFont(cell.getFont());
        cell.add(value, BorderLayout.SOUTH);
        return cell;
    }

    /** Refreshes summary + job log in the background. */
    public void refresh() {
        frame.runInBackground("Cargando logs…", () -> {
            boolean hasLog = LogViewerService.hasJobLog(db.get());
            List<JobLogEntry> entries = hasLog ? LogViewerService.loadJobLogs(db.get()) : List.of();
            DataSummary summary = LogViewerService.loadSummary(db.get());
            return new Load(entries, summary, hasLog);
        }, result -> {
            recordsLabel.setText(Long.toString(result.summary().totalRecords()));
            lastLabel.setText(result.summary().lastTimestamp() == null
                    ? "—" : result.summary().lastTimestamp().toInstant().toString());
            avgLabel.setText(result.summary().avgTotal() == null
                    ? "—" : result.summary().avgTotal().stripTrailingZeros().toPlainString() + " €");
            overridesLabel.setText(Long.toString(result.summary().overriddenColors()));
            sourceLabel.setText(result.summary().countsBySource().isEmpty()
                    ? "—"
                    : String.join(", ", result.summary().countsBySource().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue()).toList()));

            logModel.setRows(result.hasJobLog() ? result.entries() : List.of());
            fallbackNote.setText(result.hasJobLog()
                    ? " "
                    : "Log de jobs no disponible — consulta directa a PRICE_RECORD (la tabla job_log "
                            + "solo existe en la demo SQLite; en SQL Server créela manualmente, ver README).");
        });
    }

    /** Background worker payload. */
    private record Load(List<JobLogEntry> entries, DataSummary summary, boolean hasJobLog) {
    }

    // ------------------------------------------------------------- log table model

    private static final class LogTableModel extends AbstractTableModel {
        private List<JobLogEntry> rows = List.of();

        void setRows(List<JobLogEntry> entries) {
            this.rows = entries == null ? List.of() : entries;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return LOG_COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return LOG_COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            JobLogEntry e = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.jobName();
                case 1 -> e.status();
                case 2 -> prettify(e.startedAt());
                case 3 -> prettify(e.finishedAt());
                case 4 -> e.rowsAffected();
                case 5 -> e.message() == null ? "" : e.message();
                default -> "";
            };
        }

        private String prettify(String iso) {
            if (iso == null || iso.isBlank()) {
                return "";
            }
            try {
                return OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneOffset.UTC)
                        .format(TS_FORMAT);
            } catch (Exception ex) {
                return iso;
            }
        }
    }
}