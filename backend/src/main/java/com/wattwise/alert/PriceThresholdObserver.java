package com.wattwise.alert;

import com.wattwise.model.dto.PriceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fires when the day's cheapest slot is at least {@code thresholdPctBelowMean}
 * below the daily mean — i.e. "today there is a very cheap window — now is the
 * moment to run your appliances".
 *
 * <p>Pure: performs no I/O, so it is trivially unit-testable.
 */
@Component
public class PriceThresholdObserver implements AlertObserver {

    private static final Logger log = LoggerFactory.getLogger(PriceThresholdObserver.class);

    @Override
    public String name() {
        return "PRICE_THRESHOLD";
    }

    @Override
    public boolean evaluate(PriceAlertContext context) {
        BigDecimal mean = context.getDailyMeanEurPerKwh();
        BigDecimal thresholdPct = context.getThresholdPctBelowMean();
        if (mean == null || mean.signum() <= 0 || thresholdPct == null || context.getPrices().isEmpty()) {
            return false;
        }
        BigDecimal minPrice = context.getPrices().stream()
                .map(PriceDto::getTotalEurPerKwh)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        if (minPrice == null) {
            return false;
        }
        // Fire when min <= mean * (1 - threshold%).
        BigDecimal factor = BigDecimal.ONE.subtract(
                thresholdPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return minPrice.compareTo(mean.multiply(factor)) <= 0;
    }

    @Override
    public String describe(PriceAlertContext context) {
        return "Cheap window detected: cheapest slot is at least "
                + context.getThresholdPctBelowMean() + "% below today's mean";
    }
}