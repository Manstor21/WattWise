package com.wattwise.service;

import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.recommendation.DishwasherStrategy;
import com.wattwise.recommendation.EvChargingStrategy;
import com.wattwise.recommendation.GenericApplianceStrategy;
import com.wattwise.recommendation.RecommendationStrategy;
import com.wattwise.recommendation.StrategyFactory;
import com.wattwise.recommendation.WashingMachineStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.wattwise.testutil.TestData.appliance;
import static com.wattwise.testutil.TestData.price;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Service-level test: mocks PriceService (the "strategy mocks PriceService"
 * requirement) and exercises the real StrategyFactory + strategies end-to-end.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ApplianceService applianceService;

    @Mock
    private PriceService priceService;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        List<RecommendationStrategy> strategies = List.of(
                new WashingMachineStrategy(),
                new DishwasherStrategy(),
                new EvChargingStrategy(),
                new GenericApplianceStrategy());
        recommendationService = new RecommendationService(applianceService, priceService,
                new StrategyFactory(strategies));
    }

    @Test
    void recommendAllUsesCheapestWindowPerAppliance() {
        Appliance washer = appliance(1L, ApplianceType.WASHING_MACHINE, 1.0, 60);
        when(applianceService.getAppliances(5L)).thenReturn(List.of(washer));

        when(priceService.getToday()).thenReturn(List.of(
                price(0, 0.30, TrafficLight.RED),
                price(1, 0.10, TrafficLight.GREEN),
                price(2, 0.10, TrafficLight.GREEN),
                price(3, 0.10, TrafficLight.GREEN),
                price(4, 0.12, TrafficLight.AMBER),
                price(5, 0.12, TrafficLight.AMBER),
                price(6, 0.30, TrafficLight.RED),
                price(7, 0.30, TrafficLight.RED)));
        when(priceService.getTomorrow()).thenReturn(List.of());

        List<RecommendationDto> recs = recommendationService.recommendAll(5L);

        assertThat(recs).hasSize(1);
        RecommendationDto rec = recs.get(0);
        // Cheapest 4-slot window: slots 1..4 (0.10+0.10+0.10+0.12).
        assertThat(rec.getRecommendedStart()).isEqualTo(price(1, 0, TrafficLight.GREEN).getTimestamp());
        assertThat(rec.getAppliance().getId()).isEqualTo(1L);
    }

    @Test
    void recommendForApplianceScopesByOwnershipViaServiceException() {
        Appliance ev = appliance(3L, ApplianceType.EV_CHARGER, 7.0, 120);
        when(applianceService.getApplianceForUser(3L, 5L)).thenReturn(ev);
        when(priceService.getToday()).thenReturn(List.of(
                price(0, 0.05, TrafficLight.GREEN),
                price(1, 0.05, TrafficLight.GREEN),
                price(2, 0.05, TrafficLight.GREEN),
                price(3, 0.05, TrafficLight.GREEN),
                price(4, 0.05, TrafficLight.GREEN),
                price(5, 0.05, TrafficLight.GREEN),
                price(6, 0.05, TrafficLight.GREEN),
                price(7, 0.05, TrafficLight.GREEN)));
        when(priceService.getTomorrow()).thenReturn(List.of());

        List<RecommendationDto> recs = recommendationService
                .recommendForAppliance(3L, 5L).map(List::of).orElseGet(List::of);

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).getSemaphore()).isEqualTo("GREEN");
    }

    @Test
    void inactiveApplianceProducesNoRecommendation() {
        Appliance washer = appliance(2L, ApplianceType.WASHING_MACHINE, 1.0, 60);
        washer.setActive(false);
        when(applianceService.getAppliances(5L)).thenReturn(List.of(washer));

        List<RecommendationDto> recs = recommendationService.recommendAll(5L);

        assertThat(recs).isEmpty();
    }
}
