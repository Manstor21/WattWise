package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.db.QueryExecutor;
import com.wattwise.admin.model.PriceRecord;
import com.wattwise.admin.service.CsvService;
import com.wattwise.admin.service.CsvService.CsvParseResult;
import com.wattwise.admin.service.CsvService.CsvRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Importación/exportación CSV. La importación muestra una vista previa validada (filas
 * válidas/inválidas con código de color y lista de errores) antes de hacer el upsert; la
 * exportación escribe una exportación filtrada con BOM UTF-8 para que Microsoft Excel
 * (configuración regional de español) la renderice correctamente.
 */
public final class ImportExportPanel extends JPanel {

    private final DatabaseConnection db;
    private final MainFrame frame;

    private final JTextField exportFromField = new JTextField(9);
    private final JTextField exportToField = new JTextField(9);
    private final JTable previewTable;
    private final PreviewModel previewModel = new PreviewModel();
    private final JButton importValidButton = new JButton("Importar válidas");
    private final JLabel importHint = new JLabel(" ");

    private CsvParseResult lastImport;

    public ImportExportPanel(DatabaseConnection db, MainFrame frame) {
        this.db = db;
        this.frame = frame;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new GridLayout(1, 2, 12, 0));

        top.add(buildImportPanel());
        top.add(buildExportPanel());
        add(top, BorderLayout.NORTH);

        previewTable = new JTable(previewModel);
        previewTable.setRowHeight(22);
        previewTable.setFillsViewportHeight(true);
        previewTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        previewTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        previewTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        previewTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        previewTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(5).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(6).setPreferredWidth(70);
        previewTable.getColumnModel().getColumn(7).setPreferredWidth(220);
        previewTable.setDefaultRenderer(Object.class, new PreviewRenderer());

        add(new JScrollPane(previewTable), BorderLayout.CENTER);
    }

    // ------------------------------------------------------------- importación

    private JPanel buildImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new TitledBorder("Importar CSV de precios"));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton chooseButton = new JButton("Elegir archivo CSV…");
        chooseButton.addActionListener(e -> chooseImportFile());
        importValidButton.addActionListener(e -> runImport());
        importValidButton.setEnabled(false);
        actions.add(chooseButton);
        actions.add(importValidButton);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        info.add(importHint);

        panel.add(actions, BorderLayout.NORTH);
        panel.add(info, BorderLayout.CENTER);
        return panel;
    }

    private void chooseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar CSV de precios");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        frame.runInBackground("Validando CSV…", () -> {
            try (var in = Files.newInputStream(file)) {
                return CsvService.parse(in);
            }
        }, result -> {
            lastImport = result;
            showPreview(result);
        });
    }

    private void showPreview(CsvParseResult result) {
        previewModel.setRows(result.rows());
        int valid = result.valid().size();
        int invalid = result.invalid().size();
        importValidButton.setEnabled(valid > 0);
        if (!result.fileErrors().isEmpty()) {
            importHint.setText("<html><font color='red'>" + String.join("<br>", result.fileErrors())
                    + "</font></html>");
            return;
        }
        importHint.setText(valid + " válidas · " + invalid + " inválidas — "
                + "el color GREEN/AMBER/RED se guarda como override local.");
    }

    private void runImport() {
        if (lastImport == null) {
            return;
        }
        List<CsvRow> validRows = lastImport.valid();
        if (validRows.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay filas válidas para importar.",
                    "Importar CSV", JOptionPane.WARNING_MESSAGE);
            return;
        }
        frame.runInBackground("Importando precios…",
                () -> applyImport(validRows),
                stats -> {
                    JOptionPane.showMessageDialog(this,
                            "<html><b>Importación finalizada.</b><br>"
                                    + "Insertadas: " + stats.inserted() + "<br>"
                                    + "Actualizadas: " + stats.updated() + "<br>"
                                    + "Omitidas (inválidas): " + stats.omitted() + "</html>",
                            "Resultado importación", JOptionPane.INFORMATION_MESSAGE);
                    importValidButton.setEnabled(false);
                    previewModel.setRows(List.of());
                });
    }

    /**
     * Estrategia de upsert: {@code price_records.timestamp} es UNIQUE, por lo que una fila cuyo
     * timestamp ya existe es un UPDATE; en caso contrario, un INSERT. Las filas de color se
     * escriben en la tabla local {@code price_color_override}.
     */
    private ImportStats applyImport(List<CsvRow> rows) throws Exception {
        long inserted = 0;
        long updated = 0;
        for (CsvRow row : rows) {
            PriceRecord record = row.record();
            boolean exists = QueryExecutor.existsTimestamp(db.get(), record.getTimestamp());
            QueryExecutor.upsertPrice(db.get(), record);
            if (exists) {
                updated++;
            } else {
                inserted++;
            }
        }
        return new ImportStats(inserted, updated, lastImport.invalid().size());
    }

    private record ImportStats(long inserted, long updated, long omitted) {
    }

    // ------------------------------------------------------------- exportación

    private JPanel buildExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new TitledBorder("Exportar CSV de precios"));

        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;
        controls.add(new JLabel("Desde (optativo):"), gbc);
        gbc.gridx = 1;
        controls.add(exportFromField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        controls.add(new JLabel("Hasta (optativo):"), gbc);
        gbc.gridx = 1;
        controls.add(exportToField, gbc);

        JButton exportButton = new JButton("Exportar…");
        exportButton.addActionListener(e -> export());
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        controls.add(exportButton, gbc);

        JLabel note = new JLabel("<html><small>Exporta timestamp, precio, impuestos, total.<br>"
                + "Cabecera compatible con la plantilla de importación.<br>"
                + "UTF-8 con BOM para Excel (RFC 4180).</small></html>");
        note.setForeground(Color.DARK_GRAY);
        gbc.gridy = 3;
        gbc.insets = new Insets(10, 6, 6, 6);
        controls.add(note, gbc);

        panel.add(controls, BorderLayout.NORTH);
        return panel;
    }

    private void export() {
        LocalDate from = parseDate(exportFromField.getText());
        LocalDate to = parseDate(exportToField.getText());
        if (!exportFromField.getText().isBlank() && from == null
                || !exportToField.getText().isBlank() && to == null) {
            JOptionPane.showMessageDialog(this, "Formato de fecha no válido. Use yyyy-MM-dd.",
                    "Exportar CSV", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar CSV exportado");
        chooser.setSelectedFile(new java.io.File("wattwise-precios.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        frame.runInBackground("Exportando…", () -> {
            List<PriceRecord> rows = QueryExecutor.findPrices(db.get(), from, to);
            try (OutputStream out = Files.newOutputStream(target)) {
                CsvService.write(out, rows);
            }
            return rows.size();
        }, count -> JOptionPane.showMessageDialog(this,
                "Exportadas " + count + " filas a " + target,
                "Exportación completada", JOptionPane.INFORMATION_MESSAGE));
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------- modelo de vista previa

    private static final String[] PREVIEW_COLUMNS = {
            "Fila", "Timestamp", "Precio €/kWh", "Total €/kWh", "Fuente", "Color", "Estado", "Detalle"
    };

    private static final class PreviewModel extends AbstractTableModel {
        private List<CsvRow> rows = List.of();

        void setRows(List<CsvRow> rows) {
            this.rows = rows == null ? List.of() : rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return PREVIEW_COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return PREVIEW_COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CsvRow row = rows.get(rowIndex);
            PriceRecord r = row.record();
            return switch (columnIndex) {
                case 0 -> "L" + row.lineNumber();
                case 1 -> r == null ? "" : r.getTimestamp();
                case 2 -> r == null ? "" : r.getPrice();
                case 3 -> r == null ? "" : r.getTotal();
                case 4 -> r == null ? "" : r.getSourceOrDefault();
                case 5 -> r == null ? "" : (r.getColor() == null ? "" : r.getColor());
                case 6 -> row.isValid() ? "OK" : "ERROR";
                case 7 -> rowErrors(row);
                default -> "";
            };
        }

        private List<CsvRow> rows() {
            return rows;
        }

        private String rowErrors(CsvRow row) {
            StringBuilder sb = new StringBuilder();
            if (row.isValid()) {
                for (String w : row.warnings()) {
                    if (sb.length() > 0) {
                        sb.append(" · ");
                    }
                    sb.append("AVISO: ").append(w);
                }
                return sb.toString();
            }
            for (String e : row.errors()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(e);
            }
            return sb.toString();
        }
    }

    private static final class PreviewRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                PreviewModel model = (PreviewModel) table.getModel();
                List<CsvRow> rows = model.rows();
                boolean valid = row < rows.size() && rows.get(row).isValid();
                c.setBackground(valid ? new Color(228, 246, 228) : new Color(248, 226, 226));
            }
            return c;
        }
    }
}