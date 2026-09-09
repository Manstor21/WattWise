package com.wattwise.recommendation;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.wattwise.testutil.TestData.appliance;
import static com.wattwise.testutil.TestData.price;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la selección de ventana y las matemáticas de coste/ahorro de cada estrategia.
 *
 * <p>El fixture (8 slots, ciclos de 60 min → ventanas de 4 slots) contiene dos ventanas
 * con el mismo coste medio real (0.125): w0 = [G,G,G,R] (índice 0) y
 * w4 = [A,A,A,A] (índice 4). Una lavadora tolera el slot rojo (ponderado 0.62 frente a
 * 0.625) mientras que un cargador EV lo rechaza (ponderado 0.90 frente a 0.75) — una
 * diferencia real y observable impulsada por las penalizaciones por tipo.
 */
class RecommendationStrategyTest {

    private List<PriceDto> prices;

    @BeforeEach
    void setUp() {
        prices = List.of(
                price(0, 0.10, TrafficLight.GREEN),
                price(1, 0.10, TrafficLight.GREEN),
                price(2, 0.10, TrafficLight.GREEN),
                price(3, 0.20, TrafficLight.RED),
                price(4, 0.13, TrafficLight.AMBER),
                price(5, 0.12, TrafficLight.AMBER),
                price(6, 0.13, TrafficLight.AMBER),
                price(7, 0.12, TrafficLight.AMBER)
        );
    }

    private RecommendationDto recommend(RecommendationStrategy strategy, Appliance appliance) {
        Optional<RecommendationDto> opt = strategy.recommend(appliance, prices);
        assertThat(opt).isPresent();
        return opt.get();
    }

    @Test
    void washingMachineToleratesRedSlotWhenItIsCheapest() {
        WashingMachineStrategy strategy = new WashingMachineStrategy();
        Appliance washer = appliance(1L, ApplianceType.WASHING_MACHINE, 1.0, 60);

        RecommendationDto dto = recommend(strategy, washer);

        // Mejor ventana = slots 0..3 (media real 0.125, ponderado 0.62 supera a 0.625).
        assertThat(dto.getRecommendedStart()).isEqualTo(prices.get(0).getTimestamp());
        assertThat(dto.getRecommendedEnd()).isEqualTo(prices.get(0).getTimestamp().plusMinutes(60));

        // Modelo de coste: avgWindowPrice x cycleKwh.
        assertThat(dto.getEstimatedCostEur())
                .isEqualByComparingTo(new BigDecimal("0.125"));
        // Peor ventana = slots 3..6 (0.20,0.13,0.12,0.13) media real 0.145.
        assertThat(dto.getWorstCaseCostEur())
                .isEqualByComparingTo(new BigDecimal("0.145"));
        assertThat(dto.getEstimatedSavingsEur())
                .isEqualByComparingTo(new BigDecimal("0.020"));
        assertThat(dto.getSemaphore()).isEqualTo("GREEN"); // color dominante entre G,G,G,R
    }

    @Test
    void dishwasherMatchesWashingMachineBehavior() {
        DishwasherStrategy strategy = new DishwasherStrategy();
        Appliance dishwasher = appliance(2L, ApplianceType.DISHWASHER, 0.9, 60);

        RecommendationDto dto = recommend(strategy, dishwasher);
        assertThat(dto.getRecommendedStart()).isEqualTo(prices.get(0).getTimestamp());
        assertThat(dto.getEstimatedCostEur())
                .isEqualByComparingTo(new BigDecimal("0.1125")); // 0.125 * 0.9
    }

    @Test
    void evChargerAvoidsRedSlotInDollarTie() {
        EvChargingStrategy strategy = new EvChargingStrategy();
        Appliance ev = appliance(3L, ApplianceType.EV_CHARGER, 7.0, 60);

        RecommendationDto dto = recommend(strategy, ev);

        // Misma media real (0.125) pero gana la ventana toda-AMBER (slots 4..7).
        assertThat(dto.getRecommendedStart()).isEqualTo(prices.get(4).getTimestamp());
        assertThat(dto.getRecommendedEnd()).isEqualTo(prices.get(4).getTimestamp().plusMinutes(60));
        assertThat(dto.getEstimatedCostEur())
                .isEqualByComparingTo(new BigDecimal("0.875")); // 0.125 * 7.0
    }

    @Test
    void genericApplianceAvoidsRedSlot() {
        GenericApplianceStrategy strategy = new GenericApplianceStrategy();
        Appliance other = appliance(4L, ApplianceType.OTHER, 1.2, 60);

        RecommendationDto dto = recommend(strategy, other);
        assertThat(dto.getRecommendedStart()).isEqualTo(prices.get(4).getTimestamp());
    }

    @Test
    void returnsEmptyWhenNotEnoughData() {
        WashingMachineStrategy strategy = new WashingMachineStrategy();
        Appliance longCycle = appliance(5L, ApplianceType.WASHING_MACHINE, 1.0, 240); // se necesitan 16 slots

        Optional<RecommendationDto> dto = strategy.recommend(longCycle, prices);
        assertThat(dto).isEmpty();
    }

    @Test
    void returnsEmptyForNullPricesOrEmptyList() {
        WashingMachineStrategy strategy = new WashingMachineStrategy();
        Appliance washer = appliance(1L, ApplianceType.WASHING_MACHINE, 1.0, 60);
        assertThat(strategy.recommend(washer, null)).isEmpty();
        assertThat(strategy.recommend(washer, List.of())).isEmpty();
    }

    @Test
    void dominantColorOfAllGreenWindowIsGreen() {
        List<PriceDto> allGreen = List.of(
                price(0, 0.05, TrafficLight.GREEN),
                price(1, 0.05, TrafficLight.GREEN),
                price(2, 0.05, TrafficLight.GREEN),
                price(3, 0.05, TrafficLight.GREEN));
        WashingMachineStrategy strategy = new WashingMachineStrategy();
        Appliance washer = appliance(1L, ApplianceType.WASHING_MACHINE, 1.0, 60);

        RecommendationDto dto = strategy.recommend(washer, allGreen).orElseThrow();
        assertThat(dto.getSemaphore()).isEqualTo("GREEN");
    }

    @Test
    void strategyFactoryDispatchesByType() {
        StrategyFactory factory = new StrategyFactory(List.of(
                new WashingMachineStrategy(),
                new DishwasherStrategy(),
                new EvChargingStrategy(),
                new GenericApplianceStrategy()));

        assertThat(factory.strategyFor(appliance(1L, ApplianceType.WASHING_MACHINE, 1.0, 60)))
                .isInstanceOf(WashingMachineStrategy.class);
        assertThat(factory.strategyFor(appliance(2L, ApplianceType.DISHWASHER, 1.0, 60)))
                .isInstanceOf(DishwasherStrategy.class);
        assertThat(factory.strategyFor(appliance(3L, ApplianceType.EV_CHARGER, 1.0, 60)))
                .isInstanceOf(EvChargingStrategy.class);
        // Los tipos sin estrategia dedicada recurren a la genérica.
        assertThat(factory.strategyFor(appliance(4L, ApplianceType.DRYER, 1.0, 60)))
                .isInstanceOf(GenericApplianceStrategy.class);
        assertThat(factory.strategyFor(appliance(5L, ApplianceType.POOL_PUMP, 1.0, 60)))
                .isInstanceOf(GenericApplianceStrategy.class);
        assertThat(factory.strategyFor(appliance(6L, ApplianceType.AC, 1.0, 60)))
                .isInstanceOf(GenericApplianceStrategy.class);
        assertThat(factory.strategyFor(appliance(7L, ApplianceType.OTHER, 1.0, 60)))
                .isInstanceOf(GenericApplianceStrategy.class);
    }
}