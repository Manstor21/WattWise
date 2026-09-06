package com.wattwise.service;

import com.wattwise.model.dto.EsiosPricePoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Client for the public ESIOS API of Red Eléctrica de España (REE).
 *
 * <p>{@code https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real}
 *
 * <p>Defined as an interface so the scheduled job can be tested against a mock —
 * the real HTTP call is isolated in {@link EsiosClientServiceImpl}.
 */
public interface EsiosClientService {

    /**
     * Fetch day-ahead prices for {@code date} (Spain local time; the request window
     * covers the full 24h local day).
     *
     * @param date Spanish local date to fetch
     * @return parsed price points in chronological order
     */
    List<EsiosPricePoint> fetchPricesForDate(LocalDate date);
}