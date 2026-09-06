package com.wattwise.alert;

import com.wattwise.model.dto.PriceDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable context handed to all {@link AlertObserver}s on each check. Carries
 * the day's prices, the daily mean and the user's threshold configuration so
 * observers stay pure (no I/O, easily unit-tested).
 */
public class PriceAlertContext {

    private final List<PriceDto> prices;
    private final BigDecimal dailyMeanEurPerKwh;
    private final BigDecimal thresholdPctBelowMean;

    public PriceAlertContext(List<PriceDto> prices, BigDecimal dailyMeanEurPerKwh,
                             BigDecimal thresholdPctBelowMean) {
        this.prices = prices;
        this.dailyMeanEurPerKwh = dailyMeanEurPerKwh;
        this.thresholdPctBelowMean = thresholdPctBelowMean;
    }

    public List<PriceDto> getPrices() {
        return prices;
    }

    public BigDecimal getDailyMeanEurPerKwh() {
        return dailyMeanEurPerKwh;
    }

    /** e.g. 20 means "fire when today's best price is >= 20% below the mean". */
    public BigDecimal getThresholdPctBelowMean() {
        return thresholdPctBelowMean;
    }
}