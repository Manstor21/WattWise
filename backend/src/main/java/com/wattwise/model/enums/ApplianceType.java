package com.wattwise.model.enums;

/**
 * Tipos de electrodoméstico. Hay una relación 1:1 entre un ApplianceType y una
 * implementación de RecommendationStrategy (buscada vía StrategyFactory).
 */
public enum ApplianceType {
    WASHING_MACHINE,
    DISHWASHER,
    EV_CHARGER,
    DRYER,
    POOL_PUMP,
    AC,
    OTHER
}
