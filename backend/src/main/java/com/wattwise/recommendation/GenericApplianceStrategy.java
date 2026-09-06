package com.wattwise.recommendation;

import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

import java.util.Map;

/**
 * Fallback strategy used for appliance types without a dedicated implementation
 * (DRYER, POOL_PUMP, AC, OTHER) and as a default for unknown types. Penalties are
 * drawn from the methodology's per-appliance preference table:
 *
 * <ul>
 *   <li>Dryer: green 60 / amber 30 / red 10</li>
 *   <li>Pool pump: green 75 / amber 20 / red 5</li>
 *   <li>AC/heat pump: green 50 / amber 30 / red 20 (comfort override)</li>
 *   <li>Other: green 65 / amber 25 / red 10</li>
 * </ul>
 */
public class GenericApplianceStrategy extends AbstractApplianceStrategy {

    private static final Map<ApplianceType, double[]> PENALTIES = Map.of(
            ApplianceType.DRYER, new double[]{1.0, 1.15, 1.7},
            ApplianceType.POOL_PUMP, new double[]{1.0, 1.25, 1.9},
            ApplianceType.AC, new double[]{1.0, 1.10, 1.4},
            ApplianceType.OTHER, new double[]{1.0, 1.20, 1.7}
    );

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.OTHER;
    }

    @Override
    protected double penalty(TrafficLight color) {
        // Default penalties = OTHER row.
        return penaltyFor(ApplianceType.OTHER, color);
    }

    /** Allows the factory to dispatch e.g. a DRYER while keeping single-instance strategy. */
    double penaltyFor(ApplianceType type, TrafficLight color) {
        double[] p = PENALTIES.getOrDefault(type, PENALTIES.get(ApplianceType.OTHER));
        return switch (color) {
            case GREEN -> p[0];
            case AMBER -> p[1];
            case RED -> p[2];
        };
    }

    @Override
    public boolean appliesTo(Appliance appliance) {
        return PENALTIES.containsKey(appliance.getType())
                || appliance.getType() == ApplianceType.OTHER;
    }
}