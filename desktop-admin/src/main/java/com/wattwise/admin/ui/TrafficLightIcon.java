package com.wattwise.admin.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/**
 * A tiny drawn traffic-light icon (filled circle with a darker ring). Used for the colour
 * column of the price table and for the legend. No external image assets required.
 */
public final class TrafficLightIcon implements Icon {

    private static final int SIZE = 13;

    public static final Color COLOR_GREEN = new Color(46, 160, 67);
    public static final Color COLOR_AMBER = new Color(230, 152, 0);
    public static final Color COLOR_RED = new Color(214, 69, 65);
    public static final Color COLOR_UNKNOWN = new Color(150, 150, 150);

    private final Color fill;

    public TrafficLightIcon(String colorName) {
        this.fill = resolve(colorName);
    }

    public TrafficLightIcon(Color fill) {
        this.fill = fill == null ? COLOR_UNKNOWN : fill;
    }

    /** Maps a traffic-light name to its colour; anything else renders grey. */
    public static Color resolve(String colorName) {
        if (colorName == null) {
            return COLOR_UNKNOWN;
        }
        return switch (colorName.trim().toUpperCase()) {
            case "GREEN" -> COLOR_GREEN;
            case "AMBER" -> COLOR_AMBER;
            case "RED" -> COLOR_RED;
            default -> COLOR_UNKNOWN;
        };
    }

    /** Soft pastel background used to tint table rows by state. */
    public static Color pastel(String colorName) {
        Color c = resolve(colorName);
        if (c == COLOR_UNKNOWN) {
            return null;
        }
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 38);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillOval(x, y, SIZE, SIZE);
        g2.setColor(fill.darker());
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawOval(x, y, SIZE, SIZE);
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }
}