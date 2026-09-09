package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Estrategia para lavavajillas. Igual que la lavadora (verde 70 % / ámbar 25 % /
 * rojo 5 %): fuerte preferencia por el verde, rojo solo si no existe nada mejor.
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