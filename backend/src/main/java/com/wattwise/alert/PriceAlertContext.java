package com.wattwise.alert;

import com.wattwise.model.dto.PriceDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contexto inmutable entregado a todos los {@link AlertObserver} en cada comprobación.
 * Transporta los precios del día, la media diaria y la configuración de umbral del
 * usuario para que los observers se mantengan puros (sin E/S, fácilmente comprobados
 * con tests unitarios).
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

    /** p. ej. 20 significa "disparar cuando el mejor precio de hoy esté >= 20 % por debajo de la media". */
    public BigDecimal getThresholdPctBelowMean() {
        return thresholdPctBelowMean;
    }
}