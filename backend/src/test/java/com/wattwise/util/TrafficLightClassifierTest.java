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
        // 96 slots: 0.05 .. 1.00. El tercio más barato debe ser GREEN, el tercio superior RED.
        List<BigDecimal> prices = rampDay(0.05, 0.01, 96);
        List<TrafficLight> result = classifier.classifyDay(prices);

        assertThat(result).hasSize(96);
        assertThat(result.get(0)).isEqualTo(TrafficLight.GREEN);
        assertThat(result.get(95)).isEqualTo(TrafficLight.RED);
        assertThat(result).contains(TrafficLight.AMBER);
        assertThat(result).contains(TrafficLight.GREEN);
        assertThat(result).contains(TrafficLight.RED);

        // El tercio inferior (índices 0..31) debe estar limpio (sin RED).
        for (int i = 0; i < 32; i++) {
            assertThat(result.get(i)).isNotEqualTo(TrafficLight.RED);
        }
        // El tercio superior (índices 64..95) debe estar limpio (sin GREEN).
        for (int i = 64; i < 96; i++) {
            assertThat(result.get(i)).isNotEqualTo(TrafficLight.GREEN);
        }
    }

    @Test
    void uniformFlatDayProducesAmberWhenAbsoluteLayerIsAmber() {
        // CV = 0 ⇒ día uniforme ⇒ capa de percentiles deshabilitada.
        List<BigDecimal> prices = rampDay(0.08, 0.0, 96);
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).containsOnly(TrafficLight.AMBER);
    }

    @Test
    void uniformDayAtDeepCheapPricesIsAmberPerSpec() {
        // Día uniforme a 0.03: la capa absoluta dice GREEN, la desviación dice AMBER.
        // uniformVote(GREEN, AMBER) => AMBER según la metodología (necesita ambos GREEN).
        List<BigDecimal> prices = rampDay(0.03, 0.0, 24);
        List<TrafficLight> result = classifier.classifyDay(prices);
        assertThat(result).containsOnly(TrafficLight.AMBER);
    }

    @Test
    void uniformDayRespectsAbsoluteRed() {
        // Día uniforme a 0.30 (caro): la capa absoluta => RED domina.
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
        // Exactamente en el límite (price == absoluteGreenMax) NO es green.
        TrafficLightClassifier.Config strict = new TrafficLightClassifier.Config()
                .absoluteGreenMax(0.10)
                .absoluteRedMin(0.20);
        TrafficLightClassifier c = new TrafficLightClassifier(strict);

        List<BigDecimal> boundary = List.of(BigDecimal.valueOf(0.10));
        List<TrafficLight> atGreenBoundary = c.classifyDay(boundary);
        assertThat(atGreenBoundary.get(0)).isEqualTo(TrafficLight.AMBER);

        List<BigDecimal> redBoundary = List.of(BigDecimal.valueOf(0.20));
        // 0.20 no es > 0.20, así que RED solo vía otras capas; con un solo slot la capa 2 es AMBER.
        List<TrafficLight> atRedBoundary = c.classifyDay(redBoundary);
        assertThat(atRedBoundary.get(0)).isEqualTo(TrafficLight.AMBER);
    }

    @Test
    void islandSmoothingFillsAmberBetweenTwoGreens() {
        TrafficLightClassifier.Config cfg = new TrafficLightClassifier.Config()
                .absoluteGreenMax(0.30)
                .absoluteRedMin(0.60);
        TrafficLightClassifier c = new TrafficLightClassifier(cfg);

        // idx1 (0.35) es AMBER en las tres capas: percentil 0.333 (límite),
        // desviación -12.5% (dentro de +/-25%), absoluta 0.35 (ni barato ni rojo).
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(0.05), // G
                BigDecimal.valueOf(0.35), // A (would-be island)
                BigDecimal.valueOf(0.05), // G
                BigDecimal.valueOf(0.60), // boundary amber
                BigDecimal.valueOf(0.65), // R
                BigDecimal.valueOf(0.70)); // R
        List<TrafficLight> result = c.classifyDay(prices);

        // Con suavizado, el único AMBER entre los dos GREEN se convierte en GREEN.
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
        // Con una banda verde estrecha, el índice 0 es GREEN y el índice 9 es RED.
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