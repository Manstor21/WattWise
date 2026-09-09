package com.wattwise.recommendation;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;

import java.util.List;
import java.util.Optional;

/**
 * Patrón Strategy: una implementación por tipo de electrodoméstico. Cada estrategia
 * traduce el contexto de semáforo en una recomendación de programación específica del
 * electrodoméstico (ventana óptima dentro de sus restricciones).
 *
 * @see StrategyFactory
 */
public interface RecommendationStrategy {

    /** El tipo de electrodoméstico que atiende esta estrategia. Debe ser único por implementación. */
    ApplianceType supportedType();

    /**
     * Indica si esta estrategia gestiona {@code appliance}. Por defecto: coincide con el
     * {@link #supportedType()} declarado. Las estrategias multi-tipo lo sobreescriben.
     */
    default boolean appliesTo(Appliance appliance) {
        return supportedType() == appliance.getType();
    }

    /**
     * Calcula la mejor ventana de precios contigua para {@code appliance}.
     *
     * @param appliance el electrodoméstico del usuario
     * @param prices    slots de precio futuros clasificados (en orden cronológico)
     * @return la recomendación, o vacío cuando no hay datos suficientes
     */
    Optional<RecommendationDto> recommend(Appliance appliance, List<PriceDto> prices);
}