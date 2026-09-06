package com.wattwise.util;

import com.wattwise.model.enums.TrafficLight;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficLightClassifierTest {

    private final TrafficLightClassifier classifier = new TrafficLightClassifier();

    private List<BigDecimal> rampDay(double start, double step, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> BigDecimal.valueOf(start + i * step))
                .toList();
    }

    @Test
    void emptyListThrowsIllegalArgument() {
        assertThatThrownBy(() -> classifier.classifyDay(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> classifier.classifyDay(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativePricesAreAlwaysGreen() {
        List<BigDecimal> prices = List.of(
                new BigDecimal("-0.02"), new BigDecimal("-0.01"), new BigDecimal("0.05"),
                new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"));
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).hasSize(6);
        assertThat(result.get(0)).isEqualTo(TrafficLight.GREEN);
        assertThat(result.get(1)).isEqualTo(TrafficLight.GREEN);
    }

    @Test
    void rampDayProducesGreenAmberRedInPercentileOrder() {
        // 96 slots: 0.05 .. 1.00. Cheapest third must be GREEN, top third RED.
        List<BigDecimal> prices = rampDay(0.05, 0.01, 96);
        List<TrafficLight> result = classifier.classifyDay(prices);

        assertThat(result).hasSize(96);
        assertThat(result.get(0)).isEqualTo(TrafficLight.GREEN);
        assertThat(result.get(95)).isEqualTo(TrafficLight.RED);
        assertThat(result).contains(TrafficLight.AMBER);
        assertThat(result).contains(TrafficLight.GREEN);
        assertThat(result).contains(TrafficLight.RED);

        // Lowest third (indices 0..31) must be clean (no RED).
        for (int i = 0; i < 32; i++) {
            assertThat(result.get(i)).isNotEqualTo(TrafficLight.RED);
        }
        // Highest third (indices 64..95) must be clean (no GREEN).
        for (int i = 64; i < 96; i++) {
            assertThat(result.get(i)).isNotEqualTo(TrafficLight.GREEN);
        }
    }

    @Test
    void uniformFlatDayProducesAmberWhenAbsoluteLayerIsAmber() {
        // CV = 0 ⇒ uniform day ⇒ percentile layer disabled.
        List<BigDecimal> prices = rampDay(0.08, 0.0, 96);
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).containsOnly(TrafficLight.AMBER);
    }

    @Test
    void uniformDayAtDeepCheapPricesIsAmberPerSpec() {
        // Uniform day at 0.03: absolute layer says GREEN, deviation says AMBER.
        // uniformVote(GREEN, AMBER) => AMBER per methodology (needs both GREEN).
        List<BigDecimal> prices = rampDay(0.03, 0.0, 24);
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).containsOnly(TrafficLight.AMBER);
    }

    @Test
    void uniformDayRespectsAbsoluteRed() {
        // Uniform day at 0.30 (expensive): absolute layer => RED dominates.
        List<BigDecimal> prices = rampDay(0.30, 0.0, 24);
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).containsOnly(TrafficLight.RED);
    }

    @Test
    void singleSlotDoesNotThrow() {
        List<TrafficLight> result = classifier.classifyDay(List.of(BigDecimal.valueOf(0.10)));
        assertThat(result).hasSize(1);
    }

    @Test
    void absoluteThresholdBoundariesHonorStrictInequalities() {
        // Exactly at the boundary (price == absoluteGreenMax) is NOT green.
        TrafficLightClassifier.Config strict = new TrafficLightClassifier.Config()
                .absoluteGreenMax(0.10)
                .absoluteRedMin(0.20);
        TrafficLightClassifier c = new TrafficLightClassifier(strict);

        List<BigDecimal> boundary = List.of(BigDecimal.valueOf(0.10));
        List<TrafficLight> atGreenBoundary = c.classifyDay(boundary);
        assertThat(atGreenBoundary.get(0)).isEqualTo(TrafficLight.AMBER);

        List<BigDecimal> redBoundary = List.of(BigDecimal.valueOf(0.20));
        // 0.20 is not > 0.20, so RED only via other layers; single-slot layer2 is AMBER.
        List<TrafficLight> atRedBoundary = c.classifyDay(redBoundary);
        assertThat(atRedBoundary.get(0)).isEqualTo(TrafficLight.AMBER);
    }

    @Test
    void islandSmoothingFillsAmberBetweenTwoGreens() {
        TrafficLightClassifier.Config cfg = new TrafficLightClassifier.Config()
                .absoluteGreenMax(0.30)
                .absoluteRedMin(0.60);
        TrafficLightClassifier c = new TrafficLightClassifier(cfg);

        // idx1 (0.35) is AMBER in all three layers: percentile 0.333 (boundary),
        // deviation -12.5% (within +/-25%), absolute 0.35 (neither cheap nor red).
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(0.05), // G
                BigDecimal.valueOf(0.35), // A (would-be island)
                BigDecimal.valueOf(0.05), // G
                BigDecimal.valueOf(0.60), // boundary amber
                BigDecimal.valueOf(0.65), // R
                BigDecimal.valueOf(0.70)); // R
        List<TrafficLight> result = c.classifyDay(prices);

        // With smoothing the single AMBER between the two GREENS becomes GREEN.
        assertThat(result.get(1)).isEqualTo(TrafficLight.GREEN);
    }

    @Test
    void smoothingCanBeDisabled() {
        TrafficLightClassifier.Config disabled = new TrafficLightClassifier.Config()
                .absoluteGreenMax(0.30)
                .absoluteRedMin(0.60)
                .smoothingEnabled(false);
        TrafficLightClassifier c = new TrafficLightClassifier(disabled);

        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(0.35),
                BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(0.60),
                BigDecimal.valueOf(0.65),
                BigDecimal.valueOf(0.70));
        List<TrafficLight> result = c.classifyDay(prices);
        assertThat(result.get(1)).isEqualTo(TrafficLight.AMBER);
    }

    @Test
    void customPercentilesAreRespected() {
        TrafficLightClassifier.Config custom = new TrafficLightClassifier.Config()
                .percentileGreenMax(0.10)
                .percentileRedMin(0.90);
        TrafficLightClassifier c = new TrafficLightClassifier(custom);

        List<BigDecimal> prices = rampDay(0.05, 0.01, 10); // 0.05..0.14
        List<TrafficLight> result = c.classifyDay(prices);
        // With narrow green band, index 0 is GREEN, index 9 is RED.
        assertThat(result.get(0)).isEqualTo(TrafficLight.GREEN);
        assertThat(result.get(9)).isEqualTo(TrafficLight.RED);
    }

    @Test
    void zipfConfigurationBuilderKeepsDefaults() {
        TrafficLightClassifier.Config cfg = new TrafficLightClassifier.Config();
        assertThat(cfg.percentileGreenMax).isEqualTo(0.33);
        assertThat(cfg.percentileRedMin).isEqualTo(0.67);
        assertThat(cfg.absoluteGreenMax).isEqualTo(0.10);
        assertThat(cfg.absoluteRedMin).isEqualTo(0.20);
        assertThat(cfg.uniformDayCvThreshold).isEqualTo(0.10);
        assertThat(cfg.smoothingEnabled).isTrue();
    }
}