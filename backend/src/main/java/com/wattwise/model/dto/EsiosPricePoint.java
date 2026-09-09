package com.wattwise.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un único punto de precio crudo (sin normalizar) extraído de la respuesta JSON de ESIOS.
 * {@code valueEurPerMwh} es el valor bruto de REE (EUR/MWh). El scheduler lo convierte
 * a EUR/kWh antes de persistirlo.
 */
public record EsiosPricePoint(LocalDateTime datetime, BigDecimal valueEurPerMwh) {
}