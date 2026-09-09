package com.wattwise.service;

import com.wattwise.model.dto.EsiosPricePoint;

import java.time.LocalDate;
import java.util.List;

/**
 * Cliente de la API pública de ESIOS de Red Eléctrica de España (REE).
 *
 * <p>{@code https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real}
 *
 * <p>Definida como interfaz para que la tarea programada pueda probarse contra un mock —
 * la llamada HTTP real queda aislada en {@link EsiosClientServiceImpl}.
 */
public interface EsiosClientService {

    /**
     * Obtiene los precios day-ahead de {@code date} (hora local de España; la ventana de
     * petición cubre el día local completo de 24 h).
     *
     * @param date fecha local española que se desea obtener
     * @return puntos de precio parseados en orden cronológico
     */
    List<EsiosPricePoint> fetchPricesForDate(LocalDate date);
}