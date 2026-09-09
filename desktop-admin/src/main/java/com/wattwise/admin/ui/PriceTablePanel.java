package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.db.QueryExecutor;
import com.wattwise.admin.model.PriceRecord;
import com.wattwise.admin.service.PriceCorrectionService;
import com.wattwise.admin.service.PriceCorrectionService.CorrectionReport;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tabla de precios con corrección en línea. Muestra todas las columnas de {@code price_records}
 * más el color de semáforo derivado/aplicado; precio, impuestos, total y color son editables en
 * línea. Las filas pueden filtrarse por rango de fechas UTC y reordenarse por cualquier columna.
 */
public final class PriceTablePanel extends JPanel {

    private static final String[] COLUMNS = {
            "ID", "Timestamp (UTC)", "Precio €/kWh", "Impuestos €/kWh",
            "Total €/kWh", "Fuente", "Fecha", "Color"
    };
    private static final int COL_PRICE = 2;
    private static final int COL_TAX = 3;
    private static final int COL_TOTAL = 4;
    private static final int COL_COLOR = 7;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    // Heurística de semáforo usada cuando no hay un override manual guardado.
    private static final BigDecimal GREEN_UP_TO = new BigDecimal("0.160000");
    private static final BigDecimal AMBER_UP_TO = new BigDecimal("0.230000");

    private final DatabaseConnection db;
    private final MainFrame frame;

    private final JTextField fromField = new JTextField(9);
    private final JTextField toField = new JTextField(9);
    private final JLabel countLabel = new JLabel(" ");

    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == COL_PRICE || column == COL_TAX || column == COL_TOTAL || column == COL_COLOR;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Long.class;
                case 1 -> OffsetDateTime.class;
                case 2, 3, 4 -> BigDecimal.class;
                case 5, 7 -> String.class;
                case 6 -> LocalDate.class;
                default -> Object.class;
            };
        }
    };

    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTable table;
    private final JButton saveButton = new JButton("Guardar cambios");

    private List<PriceRecord> loaded = List.of();
    private int colorViewColumn = COL_COLOR;

    public PriceTablePanel(DatabaseConnection db, MainFrame frame) {
        this.db = db;
        this.frame = frame;
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(90);
        table.getColumnModel().getColumn(7).setPreferredWidth(90);

        // editores en línea para las columnas editables
        table.getColumnModel().getColumn(COL_PRICE).setCellEditor(new DefaultCellEditor(new JTextField()));
        table.getColumnModel().getColumn(COL_TAX).setCellEditor(new DefaultCellEditor(new JTextField()));
        table.getColumnModel().getColumn(COL_TOTAL).setCellEditor(new DefaultCellEditor(new JTextField()));
        table.getColumnModel().getColumn(COL_COLOR).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(new String[]{"", "GREEN", "AMBER", "RED"})));
        table.setDefaultRenderer(Object.class, new PriceRowRenderer());
        colorViewColumn = table.convertColumnIndexToView(COL_COLOR);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(6, 8, 6, 8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.add(new JLabel("Desde (yyyy-MM-dd):"));
        filters.add(fromField);
        filters.add(new JLabel("Hasta:"));
        filters.add(toField);

        JButton filterButton = new JButton("Filtrar");
        filterButton.addActionListener(e -> refresh());
        JButton allButton = new JButton("Mostrar todos");
        allButton.addActionListener(e -> {
            fromField.setText("");
            toField.setText("");
            refresh();
        });
        filters.add(filterButton);
        filters.add(allButton);

        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveChanges());
        filters.add(saveButton);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        legend.add(legendLabel("Económico", "GREEN"));
        legend.add(legendLabel("Medio", "AMBER"));
        legend.add(legendLabel("Caro", "RED"));
        legend.add(countLabel);

        bar.add(filters, BorderLayout.CENTER);
        bar.add(legend, BorderLayout.EAST);
        return bar;
    }

    private JLabel legendLabel(String text, String colorName) {
        JLabel label = new JLabel(text, new TrafficLightIcon(colorName), SwingConstants.CENTER);
        label.setIconTextGap(4);
        return label;
    }

    // ------------------------------------------------------------- carga / guardado

    /** Recarga los datos en segundo plano usando los filtros de fecha actuales. */
    public void refresh() {
        LocalDate from = parseDate(fromField.getText());
        LocalDate to = parseDate(toField.getText());
        if (!fromField.getText().isBlank() && from == null
                || !toField.getText().isBlank() && to == null) {
            JOptionPane.showMessageDialog(this,
                    "Formato de fecha no válido. Use yyyy-MM-dd.", "Filtro incorrecto",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        saveButton.setEnabled(false);
        frame.runInBackground("Cargando precios…",
                () -> QueryExecutor.findPrices(db.get(), from, to),
                rows -> {
                    loaded = new ArrayList<>(rows);
                    populate(rows);
                    countLabel.setText(rows.size() + " registros");
                    frame.setStatus(describeConnection());
                });
    }

    private void populate(List<PriceRecord> rows) {
        model.setRowCount(0);
        for (PriceRecord r : rows) {
            model.addRow(new Object[]{
                    r.getId(),
                    r.getTimestamp(),
                    r.getPrice(),
                    r.getPlusTax(),
                    r.getTotal(),
                    r.getSourceOrDefault(),
                    r.getDate(),
                    effectiveColor(r)
            });
        }
        saveButton.setEnabled(true);
    }

    private void saveChanges() {
        List<PriceRecord> changes = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (int viewRow = 0; viewRow < model.getRowCount(); viewRow++) {
            int modelRow = sorter.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= loaded.size()) {
                continue;
            }
            PriceRecord original = loaded.get(modelRow);
            try {
                BigDecimal price = asDecimal(model.getValueAt(modelRow, COL_PRICE), true);
                BigDecimal tax = asDecimal(model.getValueAt(modelRow, COL_TAX), false);
                BigDecimal total = asDecimal(model.getValueAt(modelRow, COL_TOTAL), true);
                String color = normColor(model.getValueAt(modelRow, COL_COLOR));

                if (isUnchanged(original, price, tax, total, color)) {
                    continue;
                }

                PriceRecord candidate = new PriceRecord(original.getId(), original.getTimestamp(),
                        price, tax, total, original.getSourceOrDefault(), color);
                PriceCorrectionService.ValidationResult validation =
                        PriceCorrectionService.validate(candidate);
                if (!validation.ok()) {
                    problems.add("Fila " + original.getId() + " (" + original.getTimestamp() + "): "
                            + String.join("; ", validation.errors()));
                } else {
                    changes.add(candidate);
                }
            } catch (NumberFormatException ex) {
                problems.add("Fila " + original.getId() + " (" + original.getTimestamp()
                        + "): valor numérico no válido: " + ex.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "<html>Correcciones descartadas por errores de validación:<br>"
                            + String.join("<br>", problems) + "</html>",
                    "Validación", JOptionPane.WARNING_MESSAGE);
        }
        if (changes.isEmpty()) {
            if (problems.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay cambios pendientes.",
                        "Guardar cambios", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        CorrectionReport[] reportHolder = new CorrectionReport[1];
        frame.runInBackground("Guardando correcciones…",
                () -> PriceCorrectionService.apply(db.get(), changes),
                report -> {
                    reportHolder[0] = report;
                    JOptionPane.showMessageDialog(this,
                            "Se actualizaron " + report.updated() + " de " + changes.size()
                                    + " filas corregidas.",
                            "Corrección aplicada", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                });
    }

    private boolean isUnchanged(PriceRecord original, BigDecimal price, BigDecimal tax,
                                BigDecimal total, String color) {
        return original.getPrice().compareTo(price) == 0
                && Objects.equals(original.getPlusTax(), tax)
                && original.getTotal().compareTo(total) == 0
                && Objects.equals(original.getColor(), color);
    }

    // ------------------------------------------------------------- utilidades

    private String effectiveColor(PriceRecord r) {
        if (r.hasColorOverride()) {
            return r.getColor();
        }
        BigDecimal total = r.getTotal();
        if (total == null) {
            return null;
        }
        if (total.compareTo(GREEN_UP_TO) < 0) {
            return PriceRecord.COLOR_GREEN;
        }
        if (total.compareTo(AMBER_UP_TO) < 0) {
            return PriceRecord.COLOR_AMBER;
        }
        return PriceRecord.COLOR_RED;
    }

    private static BigDecimal asDecimal(Object value, boolean required) throws NumberFormatException {
        if (value == null) {
            if (required) {
                throw new NumberFormatException("valor vacío");
            }
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        String text = value.toString().trim().replace(',', '.');
        if (text.isEmpty()) {
            if (required) {
                throw new NumberFormatException("valor vacío");
            }
            return null;
        }
        return new BigDecimal(text).setScale(6, RoundingMode.HALF_UP);
    }

    private static String normColor(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text.toUpperCase();
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

    private String describeConnection() {
        return db.getMode() == DatabaseConnection.Mode.SQLITE
                ? "SQLite demo"
                : "SQL Server (" + db.getUser() + ")";
    }

    // ------------------------------------------------------------- renderizador

    private final class PriceRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            JLabel label = (JLabel) c;

            // Preferir el valor actual del modelo (refleja los edits en línea) sobre la base cargada.
            int modelRow = t.convertRowIndexToModel(row);
            Object modelColor = modelRow >= 0 ? model.getValueAt(modelRow, COL_COLOR) : null;
            String colorName = (modelColor instanceof String s && !s.isBlank())
                    ? s.toUpperCase()
                    : (modelRow >= 0 && modelRow < loaded.size()) ? effectiveColor(loaded.get(modelRow)) : null;

            if (!isSelected) {
                Color pastel = TrafficLightIcon.pastel(colorName);
                if (pastel != null) {
                    label.setBackground(pastel);
                }
            }

            if (column == colorViewColumn) {
                label.setIcon(new TrafficLightIcon(colorName));
                label.setText(colorName == null ? "—" : colorName);
                label.setHorizontalAlignment(CENTER);
                label.setToolTipText("Color semáforo (override local)");
            } else {
                label.setIcon(null);
                label.setHorizontalAlignment(LEADING);
                if (value instanceof OffsetDateTime odt) {
                    label.setText(odt.toInstant().atOffset(java.time.ZoneOffset.UTC).format(TS_FORMAT));
                } else if (value instanceof BigDecimal bd) {
                    label.setText(bd.stripTrailingZeros().toPlainString());
                } else if (value == null) {
                    label.setText("");
                }
            }
            return label;
        }
    }
}