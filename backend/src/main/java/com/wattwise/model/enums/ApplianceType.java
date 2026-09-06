package com.wattwise.model.enums;

/**
 * Appliance types. There is a 1:1 relationship between an ApplianceType and a
 * RecommendationStrategy implementation (looked up via StrategyFactory).
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
