package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.db.DatabaseConnection.Mode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Diálogo de conexión de inicio. Dos modos:
 * <ul>
 *   <li><b>SQLite (demo local)</b> — ruta de archivo; si el archivo no existe se crea y se
 *       siembra.</li>
 *   <li><b>SQL Server (producción)</b> — URL JDBC + credenciales. La contraseña se mantiene en
 *       memoria y NUNCA se escribe en el archivo de preferencias.</li>
 * </ul>
 * </p>
 * El último modo usado (y los parámetros no secretos) se persiste en
 * {@code ~/.wattwise-admin.properties}.
 */
public final class ConnectionDialog extends JDialog {

    /** Archivo de preferencias con el último modo de conexión, pero NUNCA la contraseña. */
    public static final Path PREFS_PATH = Paths.get(System.getProperty("user.home"), ".wattwise-admin.properties");

    private static final String KEY_MODE = "db.mode";
    private static final String KEY_SQLITE_FILE = "sqlite.file";
    private static final String KEY_SQLSERVER_URL = "sqlserver.url";
    private static final String KEY_SQLSERVER_USER = "sqlserver.user";
    private static final String DEFAULT_SQLSERVER_URL =
            "jdbc:sqlserver://localhost:1433;databaseName=wattwise;encrypt=true;trustServerCertificate=true";

    private final Properties prefs = new Properties();

    private final JRadioButton sqliteRadio = new JRadioButton("SQLite (demo local)", true);
    private final JRadioButton sqlserverRadio = new JRadioButton("SQL Server (producción)");
    private final JTextField sqliteFileField = new JTextField();
    private final JTextField sqlUrlField = new JTextField(DEFAULT_SQLSERVER_URL);
    private final JTextField sqlUserField = new JTextField();
    private final JPasswordField sqlPassField = new JPasswordField();
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton connectButton = new JButton("Conectar");
    private final JButton testButton = new JButton("Probar conexión");
    private final JButton cancelButton = new JButton("Cancelar");

    private DatabaseConnection result;

    private ConnectionDialog(Window owner) {
        super(owner, "WattWise — Conexión a base de datos", ModalityType.APPLICATION_MODAL);
        loadPreferences();
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    /**
     * Muestra el diálogo modal y devuelve el {@link DatabaseConnection} conectado, o
     * {@code null} si el usuario canceló.
     */
    public static DatabaseConnection promptAndConnect(Window owner) {
        ConnectionDialog dialog = new ConnectionDialog(owner);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void buildUi() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        ButtonGroup group = new ButtonGroup();
        group.add(sqliteRadio);
        group.add(sqlserverRadio);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        JPanel modePanel = new JPanel(new BorderLayout(8, 4));
        modePanel.setBorder(new TitledBorder("Modo de conexión"));
        JPanel modeCheck = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        modeCheck.add(sqliteRadio);
        modeCheck.add(sqlserverRadio);
        modePanel.add(modeCheck, BorderLayout.NORTH);

        JPanel sqlitePanel = new JPanel(new BorderLayout(6, 2));
        sqlitePanel.setBorder(new EmptyBorder(2, 10, 2, 10));
        sqlitePanel.add(new JLabel("Archivo SQLite:"), BorderLayout.WEST);
        sqlitePanel.add(sqliteFileField, BorderLayout.CENTER);
        JButton browse = new JButton("Examinar…");
        browse.addActionListener(e -> chooseSqliteFile());
        sqlitePanel.add(browse, BorderLayout.EAST);

        JPanel serverPanel = new JPanel(new GridBagLayout());
        serverPanel.setBorder(new EmptyBorder(2, 10, 2, 10));
        GridBagConstraints s = new GridBagConstraints();
        s.insets = new Insets(2, 0, 2, 6);
        s.anchor = GridBagConstraints.WEST;
        s.gridx = 0;
        s.gridy = 0;
        serverPanel.add(new JLabel("URL JDBC:"), s);
        s.gridx = 1;
        s.weightx = 1;
        s.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(sqlUrlField, s);
        s.gridx = 0;
        s.gridy = 1;
        s.weightx = 0;
        s.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel("Usuario:"), s);
        s.gridx = 1;
        s.weightx = 1;
        s.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(sqlUserField, s);
        s.gridx = 0;
        s.gridy = 2;
        s.weightx = 0;
        s.fill = GridBagConstraints.NONE;
        serverPanel.add(new JLabel("Contraseña:"), s);
        s.gridx = 1;
        s.weightx = 1;
        s.fill = GridBagConstraints.HORIZONTAL;
        serverPanel.add(sqlPassField, s);

        JPanel settings = new JPanel(new CardLayout());
        settings.add(serverPanel, "server");
        settings.add(sqlitePanel, "sqlite");
        sqliteRadio.addActionListener(e -> ((CardLayout) settings.getLayout()).show(settings, "sqlite"));
        sqlserverRadio.addActionListener(e -> ((CardLayout) settings.getLayout()).show(settings, "server"));
        if (prefs.getProperty(KEY_MODE, "sqlite").equalsIgnoreCase("sqlserver")) {
            sqlserverRadio.setSelected(true);
            sqliteRadio.setSelected(false);
            ((CardLayout) settings.getLayout()).show(settings, "server");
        }

        JLabel note = new JLabel("<html><small>La contraseña se mantiene solo en memoria y nunca se guarda en disco.<br>"
                + "SQLite: si el archivo no existe se crea con esquema demo y datos de ejemplo.</small></html>");
        note.setForeground(Color.DARK_GRAY);

        cancelButton.addActionListener(e -> dispose());

        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        root.add(modePanel, gbc);
        gbc.gridy = 1;
        root.add(settings, gbc);
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        root.add(progressBar, gbc);
        gbc.gridy = 3;
        gbc.insets = new Insets(8, 4, 0, 4);
        root.add(note, gbc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        testButton.addActionListener(e -> testConnection());
        connectButton.addActionListener(e -> connect());
        buttons.add(testButton);
        buttons.add(connectButton);
        buttons.add(cancelButton);
        gbc.gridy = 4;
        gbc.insets = new Insets(8, 4, 0, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        root.add(buttons, gbc);

        getContentPane().add(root);
        sqliteFileField.setText(prefs.getProperty(KEY_SQLITE_FILE, DatabaseConnection.DEFAULT_SQLITE_FILE));
        sqlUrlField.setText(prefs.getProperty(KEY_SQLSERVER_URL, DEFAULT_SQLSERVER_URL));
        sqlUserField.setText(prefs.getProperty(KEY_SQLSERVER_USER, ""));
    }

    private void chooseSqliteFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar archivo SQLite");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Base de datos SQLite (*.db)", "db", "sqlite", "sqlite3"));
        int option = chooser.showSaveDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            sqliteFileField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ------------------------------------------------------------- acciones

    private void testConnection() {
        disableControls();
        progressBar.setVisible(true);
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try (DatabaseConnection probe = buildConnection()) {
                    probe.testConnection();
                    return "Conexión correcta (" + probe.getMode() + ")";
                } catch (SQLException | IOException e) {
                    return "Error: " + friendly(e);
                }
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                enableControls();
                try {
                    JOptionPane.showMessageDialog(ConnectionDialog.this, get(),
                            "Prueba de conexión", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | CancellationException | ExecutionException e) {
                    showUnexpected(e);
                }
            }
        };
        worker.execute();
    }

    private void connect() {
        disableControls();
        progressBar.setVisible(true);
        SwingWorker<DatabaseConnection, Void> worker = new SwingWorker<>() {
            @Override
            protected DatabaseConnection doInBackground() throws Exception {
                DatabaseConnection db = buildConnection();
                if (!db.testConnection()) {
                    db.close();
                    throw new SQLException("La conexión no respondió a SELECT 1");
                }
                return db;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                enableControls();
                try {
                    result = get();
                    savePreferences();
                    dispose();
                } catch (InterruptedException | CancellationException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    JOptionPane.showMessageDialog(ConnectionDialog.this,
                            "<html><b>No se pudo conectar.</b><br>" + friendly(e.getCause()) + "</html>",
                            "Error de conexión", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void showUnexpected(Exception e) {
        JOptionPane.showMessageDialog(this, "Error inesperado: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void disableControls() {
        connectButton.setEnabled(false);
        testButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    private void enableControls() {
        connectButton.setEnabled(true);
        testButton.setEnabled(true);
        cancelButton.setEnabled(true);
    }

    private DatabaseConnection buildConnection() throws SQLException, IOException {
        if (sqliteRadio.isSelected()) {
            return DatabaseConnection.connectSqlite(Paths.get(sqliteFileField.getText().trim()));
        }
        String url = sqlUrlField.getText().trim();
        String user = sqlUserField.getText().trim();
        String password = new String(sqlPassField.getPassword());
        return DatabaseConnection.connectSqlServer(url, user, password);
    }

    // ------------------------------------------------------------- preferencias

    private void loadPreferences() {
        if (!Files.exists(PREFS_PATH)) {
            prefs.setProperty(KEY_MODE, "sqlite");
            prefs.setProperty(KEY_SQLITE_FILE, DatabaseConnection.DEFAULT_SQLITE_FILE);
            return;
        }
        try (InputStream in = Files.newInputStream(PREFS_PATH)) {
            prefs.load(in);
        } catch (IOException e) {
            System.err.println("No se pudieron leer las preferencias: " + e.getMessage());
        }
    }

    /** Persiste el modo y los parámetros no secretos. La contraseña se excluye deliberadamente. */
    private void savePreferences() {
        prefs.setProperty(KEY_MODE, sqliteRadio.isSelected() ? "sqlite" : "sqlserver");
        prefs.setProperty(KEY_SQLITE_FILE, sqliteFileField.getText().trim());
        prefs.setProperty(KEY_SQLSERVER_URL, sqlUrlField.getText().trim());
        prefs.setProperty(KEY_SQLSERVER_USER, sqlUserField.getText().trim());
        prefs.remove(KEY_MODE + ".password"); // nunca persistir contraseñas
        try (OutputStream out = Files.newOutputStream(PREFS_PATH)) {
            prefs.store(out, "WattWise Admin connection preferences (password never stored)");
        } catch (IOException e) {
            System.err.println("No se pudieron guardar las preferencias: " + e.getMessage());
        }
    }

    private static String friendly(Throwable t) {
        if (t instanceof SQLException sqle) {
            return sqle.getMessage() != null ? sqle.getMessage() : t.getClass().getSimpleName();
        }
        if (t instanceof IOException ioe) {
            return ioe.getMessage() != null ? ioe.getMessage() : t.getClass().getSimpleName();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}