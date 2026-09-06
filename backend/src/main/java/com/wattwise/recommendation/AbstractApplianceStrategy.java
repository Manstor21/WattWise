package com.wattwise.recommendation;

import com.wattwise.model.dto.ApplianceDto;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.TrafficLight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared window-search logic for all strategies.
 *
 * <p>The search enumerates every contiguous window of the required length and ranks
 * them by a <em>weighted</em> cost: the real price of each slot multiplied by a color
 * penalty. The penalty encodes the strategy's green/amber/red preferences from
 * {@code docs/architecture/traffic-light-methodology.md} (e.g. an EV charger strongly
 * avoids red slots while an AC accepts them). The cheapest real-price window becomes
 * the worst-case baseline for the savings estimate.
 *
 * <p>The energy model: one cycle consumes {@code avgCycleKwh} spread evenly over the
 * window slots, so the run cost is {@code avgWindowPrice × cycleKwh}.
 */
public abstract class AbstractApplianceStrategy implements RecommendationStrategy {

    static final int SLOT_MINUTES = 15;

    /**
     * Multiplier applied to a slot's price based on its traffic-light color.
     * Higher = stronger avoidance. GREEN slots always rank below AMBER below RED.
     */
    protected abstract double penalty(TrafficLight color);

    @Override
    public Optional<RecommendationDto> recommend(Appliance appliance, List<PriceDto> prices) {
        if (prices == null || prices.isEmpty() || appliance == null) {
            return Optional.empty();
        }
        int windowSlots = Math.max(1, (int) Math.ceil(appliance.getEstimatedCycleMinutes() / (double) SLOT_MINUTES));
        if (prices.size() < windowSlots) {
            return Optional.empty();
        }

        BigDecimal cycleKwh = appliance.getAvgCycleKwh();
        if (cycleKwh == null || cycleKwh.signum() <= 0) {
            return Optional.empty();
        }

        Window best = null;
        Window worst = null;
        for (int start = 0; start + windowSlots <= prices.size(); start++) {
            List<PriceDto> window = new ArrayList<>(prices.subList(start, start + windowSlots));
            Window candidate = evaluate(window, cycleKwh);
            if (best == null || candidate.weighted.compareTo(best.weighted) < 0
                    || (candidate.weighted.compareTo(best.weighted) == 0
                        && candidate.real.compareTo(best.real) < 0)) {
                best = candidate;
            }
            if (worst == null || candidate.real.compareTo(worst.real) > 0) {
                worst = candidate;
            }
        }
        if (best == null || worst == null) {
            return Optional.empty();
        }

        RecommendationDto dto = new RecommendationDto();
        ApplianceDto applianceDto = toDto(appliance);
        dto.setAppliance(applianceDto);
        dto.setType(appliance.getType());
        dto.setRecommendedStart(best.window.get(0).getTimestamp());
        dto.setRecommendedEnd(best.window.get(0).getTimestamp()
                .plusMinutes(best.window.size() * SLOT_MINUTES));
        dto.setRecommendedPriceEurPerKwh(best.real.divide(BigDecimal.valueOf(best.window.size()), 6, RoundingMode.HALF_UP));
        dto.setEstimatedCostEur(best.cost);
        dto.setWorstCaseCostEur(worst.cost);
        dto.setEstimatedSavingsEur(worst.cost.subtract(best.cost));
        dto.setSavingsPercentage(worst.cost.signum() > 0
                ? worst.cost.subtract(best.cost).divide(worst.cost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO);
        dto.setSemaphore(dominantColor(best.window).name());
        dto.setWindow(best.window);
        return Optional.of(dto);
    }

    /** Rank a window: weighted cost (color penalties) + real cost + cost for savings. */
    private Window evaluate(List<PriceDto> window, BigDecimal cycleKwh) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal realSum = BigDecimal.ZERO;
        for (PriceDto p : window) {
            BigDecimal price = p.getTotalEurPerKwh() == null ? BigDecimal.ZERO : p.getTotalEurPerKwh();
            realSum = realSum.add(price);
            weightedSum = weightedSum.add(price.multiply(BigDecimal.valueOf(penalty(p.getColor()))));
        }
        BigDecimal meanReal = realSum.divide(BigDecimal.valueOf(window.size()), 6, RoundingMode.HALF_UP);
        BigDecimal cost = meanReal.multiply(cycleKwh);
        return new Window(window, weightedSum, realSum, cost);
    }

    /** Dominant color in the window; ties resolve to the most favorable (GREEN). */
    private TrafficLight dominantColor(List<PriceDto> window) {
        int greens = 0;
        int ambers = 0;
        int reds = 0;
        for (PriceDto p : window) {
            TrafficLight c = p.getColor() == null ? TrafficLight.AMBER : p.getColor();
            switch (c) {
                case GREEN -> greens++;
                case RED -> reds++;
                default -> ambers++;
            }
        }
        if (greens >= reds && greens >= ambers) return TrafficLight.GREEN;
        if (reds > greens && reds >= ambers) return TrafficLight.RED;
        return TrafficLight.AMBER;
    }

    private ApplianceDto toDto(Appliance appliance) {
        ApplianceDto dto = new ApplianceDto();
        dto.setId(appliance.getId());
        dto.setName(appliance.getName());
        dto.setType(appliance.getType());
        dto.setPowerWatts(appliance.getPowerWatts());
        dto.setAvgCycleKwh(appliance.getAvgCycleKwh());
        dto.setEstimatedCycleMinutes(appliance.getEstimatedCycleMinutes());
        dto.setIsActive(appliance.isActive());
        dto.setCreatedAt(appliance.getCreatedAt());
        return dto;
    }

    private record Window(List<PriceDto> window, BigDecimal weighted, BigDecimal real, BigDecimal cost) {
    }
}