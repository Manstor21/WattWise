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
 * Verifies window selection + cost/savings math for every strategy.
 *
 * <p>The fixture (8 slots, 60-min cycles → 4-slot windows) contains two windows
 * with identical real average cost (0.125): w0 = [G,G,G,R] (index 0) and
 * w4 = [A,A,A,A] (index 4). A washer tolerates the red slot (weighted 0.62 vs
 * 0.625) while an EV charger rejects it (weighted 0.90 vs 0.75) — a real,
 * observable difference driven by the per-type penalties.
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

        // Best window = slots 0..3 (real avg 0.125, weighted 0.62 beating 0.625).
        assertThat(dto.getRecommendedStart()).isEqualTo(prices.get(0).getTimestamp());
        assertThat(dto.getRecommendedEnd()).isEqualTo(prices.get(0).getTimestamp().plusMinutes(60));

        // Cost model: avgWindowPrice x cycleKwh.
        assertThat(dto.getEstimatedCostEur())
                .isEqualByComparingTo(new BigDecimal("0.125"));
        // Worst window = slots 3..6 (0.20,0.13,0.12,0.13) real avg 0.145.
        assertThat(dto.getWorstCaseCostEur())
                .isEqualByComparingTo(new BigDecimal("0.145"));
        assertThat(dto.getEstimatedSavingsEur())
                .isEqualByComparingTo(new BigDecimal("0.020"));
        assertThat(dto.getSemaphore()).isEqualTo("GREEN"); // dominant color across G,G,G,R
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

        // Same real average (0.125) but the all-AMBER window (slots 4..7) wins.
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
        Appliance longCycle = appliance(5L, ApplianceType.WASHING_MACHINE, 1.0, 240); // 16 slots needed

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
        // Types without a dedicated strategy fall back to the generic one.
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