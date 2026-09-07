package com.wattwise.admin;

import com.wattwise.admin.db.DatabaseConnection;
import com.wattwise.admin.ui.ConnectionDialog;
import com.wattwise.admin.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point. Configures the system look &amp; feel, then shows the connection
 * dialog; once connected the main window is opened.
 */
public final class WattWiseAdmin {

    private static final String PACKAGE_VERSION = WattWiseAdmin.class.getPackage().getImplementationVersion() == null
            ? "1.0.0" : WattWiseAdmin.class.getPackage().getImplementationVersion();

    private WattWiseAdmin() {
    }

    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            DatabaseConnection db = ConnectionDialog.promptAndConnect(null);
            if (db == null) {
                System.out.println("WattWise Admin " + PACKAGE_VERSION + " — conexión cancelada.");
                System.exit(0);
                return;
            }
            MainFrame frame = new MainFrame(db);
            frame.setVisible(true);
        });
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("No se pudo aplicar el L&F del sistema: " + e.getMessage());
        }
    }
}