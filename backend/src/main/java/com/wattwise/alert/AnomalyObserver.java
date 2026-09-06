package com.wattwise.alert;

import com.wattwise.model.dto.PriceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Fires when today contains an anomalous price — defined (per the alert
 * methodology) as any slot whose price exceeds 2× the daily mean. A signal of
 * market stress (e.g. a supply crunch) that users should know about.
 *
 * <p>Pure: performs no I/O.
 */
@Component
public class AnomalyObserver implements AlertObserver {

    private static final Logger log = LoggerFactory.getLogger(AnomalyObserver.class);
    private static final BigDecimal ANOMALY_FACTOR = new BigDecimal("2.0");

    @Override
    public String name() {
        return "ANOMALY";
    }

    @Override
    public boolean evaluate(PriceAlertContext context) {
        BigDecimal mean = context.getDailyMeanEurPerKwh();
        if (mean == null || mean.signum() <= 0 || context.getPrices().isEmpty()) {
            return false;
        }
        BigDecimal ceiling = mean.multiply(ANOMALY_FACTOR);
        return context.getPrices().stream()
                .map(PriceDto::getTotalEurPerKwh)
                .filter(java.util.Objects::nonNull)
                .anyMatch(p -> p.compareTo(ceiling) > 0);
    }

    @Override
    public String describe(PriceAlertContext context) {
        return "Price anomaly detected: at least one slot exceeds 2× today's mean";
    }
}