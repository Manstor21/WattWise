package com.wattwise.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single raw (un-normalized) price point extracted from the ESIOS JSON response.
 * {@code valueEurPerMwh} is the raw REE value (EUR/MWh). The scheduler converts it
 * to EUR/kWh before persisting.
 */
public record EsiosPricePoint(LocalDateTime datetime, BigDecimal valueEurPerMwh) {
}