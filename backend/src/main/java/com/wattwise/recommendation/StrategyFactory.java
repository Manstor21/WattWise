package com.wattwise.recommendation;

import com.wattwise.model.entity.Appliance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the {@link RecommendationStrategy} for an appliance by its type.
 * Strategies are discovered from the Spring context so new appliance types can
 * be supported by simply adding a bean.
 */
@Component
public class StrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(StrategyFactory.class);

    private final List<RecommendationStrategy> strategies;

    public StrategyFactory(List<RecommendationStrategy> strategies) {
        this.strategies = strategies;
    }

    public RecommendationStrategy strategyFor(Appliance appliance) {
        return strategies.stream()
                .filter(s -> s.appliesTo(appliance))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("No dedicated strategy for type {}; falling back to generic", appliance.getType());
                    return strategies.stream()
                            .filter(s -> s instanceof GenericApplianceStrategy)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No generic strategy registered"));
                });
    }
}