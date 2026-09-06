package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Strategy for washing machines. Preference (per methodology doc §4):
 * green 70%, amber 25%, red 5% — red slots are penalized (1.6×) but still
 * chosen when they are part of the genuinely cheapest window.
 */
public class WashingMachineStrategy extends AbstractApplianceStrategy {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.WASHING_MACHINE;
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