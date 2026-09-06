package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Strategy for EV chargers — the most price-sensitive appliance
 * (green 80% / amber 20% / red 0%). Red slots are almost never chosen because
 * the charging window is highly flexible and the cost impact is the largest of
 * any appliance type.
 */
public class EvChargingStrategy extends AbstractApplianceStrategy {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.EV_CHARGER;
    }

    @Override
    protected double penalty(TrafficLight color) {
        return switch (color) {
            case GREEN -> 1.0;
            case AMBER -> 1.5;
            case RED -> 3.0;
        };
    }
}