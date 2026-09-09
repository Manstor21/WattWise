package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Estrategia para lavadoras. Preferencia (según doc de metodología §4):
 * verde 70 %, ámbar 25 %, rojo 5 % — los slots rojos se penalizan (1,6×) pero aun así
 * se eligen cuando forman parte de la ventana genuinamente más barata.
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