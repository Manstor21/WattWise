package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.db.QueryExecutor;
import com.wattwise.admin.db.QueryExecutor.UserRow;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.List;

/**
 * Gestión de usuarios de solo lectura. Las contraseñas nunca se cargan. La activación/
 * desactivación solo está disponible cuando el esquema expone una columna {@code is_active} —
 * true en la base de datos de demostración SQLite, false en el esquema real del backend
 * (V1__init.sql define {@code role USER|ADMIN} pero sin indicador de activación), donde el
 * panel se degrada a solo lectura con una nota explicativa.
 */
public final class UserManagementPanel extends JPanel {

    private static final String[] COLUMNS = {"ID", "Usuario", "Email", "Rol", "Creado (UTC)", "Estado"};

    private final DatabaseConnection db;
    private final MainFrame frame;

    private final UserTableModel model = new UserTableModel();
    private final JTable table;
    private final JButton toggleButton = new JButton("Activar / Desactivar");
    private final JLabel noteLabel = new JLabel(" ");

    private List<UserRow> users = List.of();

    public UserManagementPanel(DatabaseConnection db, MainFrame frame) {
        this.db = db;
        this.frame = frame;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new TitledBorder("Usuarios (sin contraseñas)"));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JButton refresh = new JButton("Refrescar");
        refresh.addActionListener(e -> refresh());
        toggleButton.addActionListener(e -> toggleSelected());
        actions.add(refresh);
        actions.add(toggleButton);

        noteLabel.setForeground(new Color(90, 90, 90));
        top.add(actions, BorderLayout.NORTH);
        top.add(noteLabel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table = new JTable(model);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (table.getColumnModel().getColumnCount() > 0) {
            table.getColumnModel().getColumn(0).setPreferredWidth(50);
            table.getColumnModel().getColumn(1).setPreferredWidth(140);
            table.getColumnModel().getColumn(2).setPreferredWidth(220);
            table.getColumnModel().getColumn(3).setPreferredWidth(80);
            table.getColumnModel().getColumn(4).setPreferredWidth(130);
            table.getColumnModel().getColumn(5).setPreferredWidth(80);
        }
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void refresh() {
        frame.runInBackground("Cargando usuarios…",
                () -> QueryExecutor.findUsers(db.get()),
                result -> {
                    users = result;
                    model.setUsers(result);
                    toggleButton.setEnabled(supportsToggle());
                    noteLabel.setText(supportsToggle()
                            ? "Columna usada: users.is_active (solo demo SQLite)."
                            : "El esquema real del backend (V1__init.sql) no tiene columna de activación "
                                    + "(solo role USER|ADMIN) — vista de solo lectura.");
                });
    }

    private boolean supportsToggle() {
        try {
            return QueryExecutor.supportsUserActiveToggle(db.get());
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un usuario.", "Usuarios",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= users.size()) {
            return;
        }
        UserRow user = users.get(modelRow);
        boolean newState = !Boolean.TRUE.equals(user.isActive());
        String verb = newState ? "activar" : "desactivar";
        int confirm = JOptionPane.showConfirmDialog(this,
                "¿" + verb + " al usuario " + user.username() + "?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        frame.runInBackground("Actualizando usuario…",
                () -> QueryExecutor.setUserActive(db.get(), user.id(), newState),
                updated -> {
                    JOptionPane.showMessageDialog(this,
                            updated > 0 ? "Usuario " + verb + " correctamente."
                                    : "No se pudo actualizar: la base de datos no soporta el cambio.",
                            "Usuarios", JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                });
    }

    // ------------------------------------------------------------- modelo

    private static final class UserTableModel extends AbstractTableModel {
        private List<UserRow> rows = List.of();

        void setUsers(List<UserRow> users) {
            this.rows = users == null ? List.of() : users;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            UserRow u = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> u.id();
                case 1 -> u.username();
                case 2 -> u.email();
                case 3 -> u.role();
                case 4 -> u.createdAt();
                case 5 -> u.isActive() == null
                        ? "—" : (u.isActive() ? "Activo" : "Desactivado");
                default -> "";
            };
        }
    }
}