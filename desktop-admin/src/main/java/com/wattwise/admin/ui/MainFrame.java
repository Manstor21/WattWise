package com.wattwise.admin.ui;

import com.wattwise.admin.db.DatabaseConnection;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Ventana principal de la aplicación: un {@link JTabbedPane} con los cuatro paneles de
 * administración y una barra de estado que aloja un indicador de progreso indeterminado
 * durante el trabajo en segundo plano.
 *
 * <p>Todas las consultas de larga duración pasan por {@link #runInBackground} para que la IU
 * nunca se congele, y se serializan sobre la única conexión JDBC compartida.
 */
public final class MainFrame extends JFrame {

    private final DatabaseConnection db;

    private final JLabel statusLabel = new JLabel("Listo");
    private final JProgressBar progressBar = new JProgressBar();

    private PriceTablePanel pricePanel;
    private ImportExportPanel importExportPanel;
    private JobLogPanel jobLogPanel;
    private UserManagementPanel userPanel;

    public MainFrame(DatabaseConnection db) {
        this.db = db;
        setTitle("WattWise — Administración de datos");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        buildMenuBar();
        buildTabs();
        buildStatusBar();

        setSize(1180, 700);
        setMinimumSize(new Dimension(980, 560));
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());

        pricePanel.refresh();
        jobLogPanel.refresh();
        userPanel.refresh();
    }

    private void buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Archivo");
        JMenuItem changeConnection = new JMenuItem("Cambiar conexión…");
        changeConnection.addActionListener(e -> changeConnection());
        JMenuItem exit = new JMenuItem("Salir");
        exit.addActionListener(e -> dispose());
        fileMenu.add(changeConnection);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu helpMenu = new JMenu("Ayuda");
        JMenuItem about = new JMenuItem("Acerca de");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "<html><b>WattWise — Administración de datos</b><br>"
                        + "Conexión directa JDBC (SQL Server · SQLite demo).<br>"
                        + "Esquema: price_records, users, job_log (demo), price_color_override (demo).<br>"
                        + "Véase desktop-admin/README.md.</html>",
                "Acerca de WattWise Admin", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(about);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    private void buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        pricePanel = new PriceTablePanel(db, this);
        importExportPanel = new ImportExportPanel(db, this);
        jobLogPanel = new JobLogPanel(db, this);
        userPanel = new UserManagementPanel(db, this);

        tabs.addTab("Precios", pricePanel);
        tabs.addTab("Importar / Exportar", importExportPanel);
        tabs.addTab("Jobs", jobLogPanel);
        tabs.addTab("Usuarios", userPanel);
        getContentPane().add(tabs, BorderLayout.CENTER);
    }

    private void buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout(6, 0));
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(120, 14));
        progressBar.setVisible(false);

        status.add(statusLabel, BorderLayout.CENTER);
        status.add(progressBar, BorderLayout.EAST);
        getContentPane().add(status, BorderLayout.SOUTH);
    }

    private Image createAppIcon() {
        int size = 28;
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // tres semáforos sobre una barra redondeada oscura
        g.setColor(new Color(40, 40, 40));
        g.fillRoundRect(2, 4, size - 4, size - 8, 8, 8);
        g.setColor(TrafficLightIcon.COLOR_GREEN);
        g.fillOval(4, 8, 6, 6);
        g.setColor(TrafficLightIcon.COLOR_AMBER);
        g.fillOval(11, 8, 6, 6);
        g.setColor(TrafficLightIcon.COLOR_RED);
        g.fillOval(18, 8, 6, 6);
        g.dispose();
        return img;
    }

    private void changeConnection() {
        int choice = JOptionPane.showConfirmDialog(this,
                "¿Cambiar la conexión? Se cerrará la conexión actual.",
                "Cambiar conexión", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        db.close();
        dispose();
        DatabaseConnection next = ConnectionDialog.promptAndConnect(null);
        if (next == null) {
            System.exit(0);
        } else {
            new MainFrame(next).setVisible(true);
        }
    }

    // ------------------------------------------------------------- estado + asíncrono

    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /**
     * Ejecuta {@code work} en un hilo worker, muestra la barra de progreso indeterminada y luego
     * entrega el resultado a {@code onDone} en el EDT. Las excepciones se muestran en un diálogo
     * y la barra de estado se reinicia.
     */
    public <T> void runInBackground(String busyText, Callable<T> work, Consumer<T> onDone) {
        statusLabel.setText(busyText);
        progressBar.setVisible(true);
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    T result = get();
                    onDone.accept(result);
                    if (statusLabel.getText().equals(busyText)) {
                        statusLabel.setText("Listo");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (CancellationException e) {
                    statusLabel.setText("Cancelado");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLabel.setText("Error");
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "<html><b>Operación fallida.</b><br>"
                                    + (cause == null ? e.getMessage() : cause.getMessage())
                                    + "</html>",
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}