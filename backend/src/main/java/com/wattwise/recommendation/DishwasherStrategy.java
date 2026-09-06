package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Strategy for dishwashers. Like the washing machine (green 70% / amber 25% /
 * red 5%): strong preference for green, red only if nothing better exists.
 */
public class DishwasherStrategy extends AbstractApplianceStrategy {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.DISHWASHER;
    }

    @Override
    protected double penalty(TrafficLight color) {
        return switch (color) {
            case GREEN -> 1.0;
            case AMBER -> 1.25;
            case RED -> 1.6;
        };
    }
}