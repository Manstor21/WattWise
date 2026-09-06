package com.wattwise.testutil;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Shared builders for test fixtures (entities and DTOs).
 */
public final class TestData {

    public static final LocalDateTime BASE_UTC = LocalDateTime.of(2099, 6, 1, 0, 0);

    private TestData() {
    }

    public static PriceDto price(int slotIndex, double totalEurPerKwh, TrafficLight color) {
        LocalDateTime ts = BASE_UTC.plusMinutes(slotIndex * 15L);
        BigDecimal total = BigDecimal.valueOf(totalEurPerKwh);
        return new PriceDto((long) slotIndex + 1, ts, total, null, total, PriceSource.ESIOS, color);
    }

    public static Appliance appliance(Long id, ApplianceType type, double cycleKwh, int cycleMinutes) {
        Appliance a = new Appliance();
        if (id != null) {
            a.setId(id);
        }
        a.setType(type);
        a.setName(type.name());
        a.setPowerWatts(1000);
        a.setAvgCycleKwh(BigDecimal.valueOf(cycleKwh));
        a.setEstimatedCycleMinutes(cycleMinutes);
        a.setActive(true);
        return a;
    }

    public static RecommendationDto recommendationFrom(Appliance a, PriceDto firstSlot, int windowSlots, BigDecimal cycleKwh) {
        RecommendationDto dto = new RecommendationDto();
        dto.setType(a.getType());
        dto.setRecommendedStart(firstSlot.getTimestamp());
        dto.setRecommendedEnd(firstSlot.getTimestamp().plusMinutes(windowSlots * 15L));
        dto.setRecommendedPriceEurPerKwh(firstSlot.getTotalEurPerKwh());
        dto.setEstimatedCostEur(cycleKwh.multiply(firstSlot.getTotalEurPerKwh()));
        dto.setWorstCaseCostEur(cycleKwh.multiply(firstSlot.getTotalEurPerKwh()).add(BigDecimal.ONE));
        dto.setEstimatedSavingsEur(BigDecimal.ONE);
        dto.setSavingsPercentage(BigDecimal.TEN);
        dto.setSemaphore(firstSlot.getColor() != null ? firstSlot.getColor().name() : "AMBER");
        dto.setWindow(java.util.List.of(firstSlot));
        return dto;
    }
}