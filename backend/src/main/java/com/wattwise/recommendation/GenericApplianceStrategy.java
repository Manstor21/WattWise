package com.wattwise.recommendation;

import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.TrafficLight;

import java.util.Map;

/**
 * Estrategia de respaldo usada para tipos de electrodoméstico sin implementación dedicada
 * (DRYER, POOL_PUMP, AC, OTHER) y como valor por defecto para tipos desconocidos. Las
 * penalizaciones se extraen de la tabla de preferencias por electrodoméstico de la metodología:
 *
 * <ul>
 *   <li>Secadora: verde 60 / ámbar 30 / rojo 10</li>
 *   <li>Bomba de piscina: verde 75 / ámbar 20 / rojo 5</li>
 *   <li>AC/bomba de calor: verde 50 / ámbar 30 / rojo 20 (prioridad de confort)</li>
 *   <li>Otros: verde 65 / ámbar 25 / rojo 10</li>
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
        // Penalizaciones por defecto = fila OTHER.
        return penaltyFor(ApplianceType.OTHER, color);
    }

    /** Permite que la factory distribuya p. ej. un DRYER manteniendo una estrategia de instancia única. */
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