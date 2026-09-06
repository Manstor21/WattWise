package com.wattwise.util;

import com.wattwise.model.enums.TrafficLight;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, unit-testable hybrid traffic-light classifier.
 *
 * <p>The algorithm is described in {@code docs/architecture/traffic-light-methodology.md}
 * and combines three classification layers plus a uniform-day short-circuit and
 * optional edge smoothing:
 *
 * <ol>
 *   <li><b>Percentile layer (relative to the day)</b>: each slot is ranked against
 *       every other slot of the same day. Bottom third = GREEN, middle third = AMBER,
 *       top third = RED.</li>
 *   <li><b>Deviation layer (relative to the daily mean)</b>: how far a slot deviates
 *       from the arithmetic mean. {@code &lt; -25%} = GREEN, {@code -25%..+25%} = AMBER,
 *       {@code &gt; +25%} = RED.</li>
 *   <li><b>Absolute context layer</b>: fixed thresholds calibrated to the Spanish PVPC
 *       market (configurable). Very cheap = GREEN, expensive = RED.</li>
 * </ol>
 *
 * <p>Negative prices are always treated as deep GREEN regardless of other layers
 * (excess renewable generation in Spain).
 */
public class TrafficLightClassifier {

    /** Configuration holder (carries default values). */
    public static class Config {
        double percentileGreenMax = 0.33;
        double percentileRedMin = 0.67;
        double deviationGreenMax = -0.25;
        double deviationRedMin = 0.25;
        double absoluteGreenMax = 0.10;
        double absoluteRedMin = 0.20;
        double uniformDayCvThreshold = 0.10;
        boolean smoothingEnabled = true;

        public Config percentileGreenMax(double v) { this.percentileGreenMax = v; return this; }
        public Config percentileRedMin(double v) { this.percentileRedMin = v; return this; }
        public Config deviationGreenMax(double v) { this.deviationGreenMax = v; return this; }
        public Config deviationRedMin(double v) { this.deviationRedMin = v; return this; }
        public Config absoluteGreenMax(double v) { this.absoluteGreenMax = v; return this; }
        public Config absoluteRedMin(double v) { this.absoluteRedMin = v; return this; }
        public Config uniformDayCvThreshold(double v) { this.uniformDayCvThreshold = v; return this; }
        public Config smoothingEnabled(boolean v) { this.smoothingEnabled = v; return this; }
    }

    private final Config config;

    public TrafficLightClassifier() {
        this(new Config());
    }

    public TrafficLightClassifier(Config config) {
        this.config = config;
    }

    /**
     * Classify a full day worth of prices. The list length should typically be 96
     * (or 24 for hourly data). Returns one TrafficLight per input price, same order.
     *
     * @param pricesEurPerKwh prices of one day, in chronological order
     * @return parallel list of classifications
     * @throws IllegalArgumentException if the list is null or empty
     */
    public List<TrafficLight> classifyDay(List<BigDecimal> pricesEurPerKwh) {
        if (pricesEurPerKwh == null || pricesEurPerKwh.isEmpty()) {
            throw new IllegalArgumentException("Cannot classify an empty price list");
        }

        int n = pricesEurPerKwh.size();
        List<TrafficLight> l1 = percentileLayer(pricesEurPerKwh);
        List<TrafficLight> l2 = deviationLayer(pricesEurPerKwh);
        List<TrafficLight> l3 = absoluteLayer(pricesEurPerKwh);

        boolean uniform = isUniformDay(pricesEurPerKwh);

        List<TrafficLight> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BigDecimal price = pricesEurPerKwh.get(i);

            // Deep-green rule for negative prices (excess renewables).
            if (price != null && price.signum() < 0) {
                result.add(TrafficLight.GREEN);
                continue;
            }

            if (uniform) {
                result.add(uniformVote(l3.get(i), l2.get(i)));
            } else {
                result.add(fusionVote(l1.get(i), l2.get(i), l3.get(i)));
            }
        }

        if (config.smoothingEnabled) {
            smoothIslands(result);
        }

        return result;
    }

    /** Layer 1: bottom/middle/top thirds by within-day rank. */
    private List<TrafficLight> percentileLayer(List<BigDecimal> prices) {
        int n = prices.size();
        List<Integer> sortedIndexes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sortedIndexes.add(i);
        }
        sortedIndexes.sort(Comparator.comparing(o -> prices.get(o) == null ? BigDecimal.ZERO : prices.get(o)));

        // percentile of each slot: rank / total
        double[] percentile = new double[n];
        for (int rank = 0; rank < n; rank++) {
            percentile[sortedIndexes.get(rank)] = (double) rank / n;
        }

        List<TrafficLight> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double p = percentile[i];
            if (p < config.percentileGreenMax) {
                result.add(TrafficLight.GREEN);
            } else if (p >= config.percentileRedMin) {
                result.add(TrafficLight.RED);
            } else {
                result.add(TrafficLight.AMBER);
            }
        }
        return result;
    }

    /** Layer 2: deviation from the arithmetic mean. */
    private List<TrafficLight> deviationLayer(List<BigDecimal> prices) {
        double mean = mean(prices);
        List<TrafficLight> result = new ArrayList<>(prices.size());
        for (BigDecimal p : prices) {
            double price = p == null ? 0 : p.doubleValue();
            double deviation;
            if (mean == 0) {
                // Degenerate: mean of zero. Treat raw sign.
                deviation = price == 0 ? 0 : (price > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
            } else {
                deviation = (price - mean) / mean;
            }
            if (deviation <= config.deviationGreenMax) {
                result.add(TrafficLight.GREEN);
            } else if (deviation >= config.deviationRedMin) {
                result.add(TrafficLight.RED);
            } else {
                result.add(TrafficLight.AMBER);
            }
        }
        return result;
    }

    /** Layer 3: absolute context thresholds (EUR/kWh). */
    private List<TrafficLight> absoluteLayer(List<BigDecimal> prices) {
        List<TrafficLight> result = new ArrayList<>(prices.size());
        for (BigDecimal p : prices) {
            double price = p == null ? 0 : p.doubleValue();
            if (price < 0) {
                result.add(TrafficLight.GREEN); // deep green
            } else if (price < config.absoluteGreenMax) {
                result.add(TrafficLight.GREEN);
            } else if (price > config.absoluteRedMin) {
                result.add(TrafficLight.RED);
            } else {
                result.add(TrafficLight.AMBER);
            }
        }
        return result;
    }

    /**
     * Uniform day detection: coefficient of variation (stddev/mean) below the
     * configured threshold. On such days we skip the percentile layer entirely
     * because forcing a bottom/middle/top split on a flat price curve would be
     * misleading.
     */
    private boolean isUniformDay(List<BigDecimal> prices) {
        double mean = mean(prices);
        double stddev = stddev(prices, mean);
        if (mean == 0) {
            return stddev == 0;
        }
        double cv = stddev / mean;
        return cv < config.uniformDayCvThreshold;
    }

    /** Fusion for uniform days — no percentile layer, contests between absolute and deviation. */
    private TrafficLight uniformVote(TrafficLight l3, TrafficLight l2) {
        if (l3 == TrafficLight.GREEN && l2 == TrafficLight.GREEN) {
            return TrafficLight.GREEN;
        }
        if (l3 == TrafficLight.RED || l2 == TrafficLight.RED) {
            return TrafficLight.RED;
        }
        return TrafficLight.AMBER;
    }

    /** Normal fusion: majority of (GREEN,RED); ties reported per spec. */
    private TrafficLight fusionVote(TrafficLight l1, TrafficLight l2, TrafficLight l3) {
        int greens = 0;
        int reds = 0;
        for (TrafficLight t : new TrafficLight[]{l1, l2, l3}) {
            if (t == TrafficLight.GREEN) greens++;
            else if (t == TrafficLight.RED) reds++;
        }
        if (greens >= 2) return TrafficLight.GREEN;
        if (reds >= 2) return TrafficLight.RED;
        // Split / all-amber: default to AMBER.
        return TrafficLight.AMBER;
    }

    /** Optional island smoothing: an AMBER surrounded by identical GREEN or RED becomes that color. */
    private void smoothIslands(List<TrafficLight> result) {
        for (int i = 1; i < result.size() - 1; i++) {
            if (result.get(i) == TrafficLight.AMBER
                    && result.get(i - 1) == result.get(i + 1)) {
                result.set(i, result.get(i - 1));
            }
        }
    }

    private double mean(List<BigDecimal> prices) {
        if (prices.isEmpty()) return 0;
        double sum = 0;
        for (BigDecimal p : prices) {
            sum += p == null ? 0 : p.doubleValue();
        }
        return sum / prices.size();
    }

    private double stddev(List<BigDecimal> prices, double mean) {
        if (prices.isEmpty()) return 0;
        double sumSquares = 0;
        for (BigDecimal p : prices) {
            double d = (p == null ? 0 : p.doubleValue()) - mean;
            sumSquares += d * d;
        }
        return Math.sqrt(sumSquares / prices.size());
    }

    public Config getConfig() {
        return config;
    }
}
