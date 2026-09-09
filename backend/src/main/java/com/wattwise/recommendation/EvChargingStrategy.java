package com.wattwise.recommendation;

import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

/**
 * Estrategia para cargadores de VE, el electrodoméstico más sensible al precio
 * (verde 80 % / ámbar 20 % / rojo 0 %). Los slots rojos casi nunca se eligen porque
 * la ventana de carga es muy flexible y el impacto en coste es el mayor de todos
 * los tipos de electrodoméstico.
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