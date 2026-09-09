package com.wattwise.service;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.recommendation.RecommendationStrategy;
import com.wattwise.recommendation.StrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta las recomendaciones: monta el contexto de precios candidato (slots restantes
 * de hoy + el día completo de mañana una vez publicado) y delega la optimización real
 * en la {@link RecommendationStrategy} específica del tipo de electrodoméstico.
 */
@Service
public class RecommendationService {

    private final ApplianceService applianceService;
    private final PriceService priceService;
    private final StrategyFactory strategyFactory;

    public RecommendationService(ApplianceService applianceService, PriceService priceService,
                                 StrategyFactory strategyFactory) {
        this.applianceService = applianceService;
        this.priceService = priceService;
        this.strategyFactory = strategyFactory;
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> recommendAll(Long userId) {
        List<RecommendationDto> result = new ArrayList<>();
        for (Appliance appliance : applianceService.getAppliances(userId)) {
            Optional<RecommendationDto> rec = recommendForApplianceInternal(appliance);
            rec.ifPresent(result::add);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationDto> recommendForAppliance(Long applianceId, Long userId) {
        Appliance appliance = applianceService.getApplianceForUser(applianceId, userId);
        return recommendForApplianceInternal(appliance);
    }

    private Optional<RecommendationDto> recommendForApplianceInternal(Appliance appliance) {
        if (!appliance.isActive()) {
            return Optional.empty();
        }
        List<PriceDto> prices = assembleCandidatePrices();
        if (prices.size() < 2) {
            return Optional.empty();
        }
        RecommendationStrategy strategy = strategyFactory.strategyFor(appliance);
        return strategy.recommend(appliance, prices);
    }

    /**
     * Precios candidatos = slots de hoy aún futuros + el día completo de mañana
     * (si se ha publicado). Las ventanas pueden cruzar la medianoche cuando una ejecución
     * de electrodoméstico continúa pasada la medianoche (p. ej. carga nocturna de VE),
     * lo cual es válido.
     */
    private List<PriceDto> assembleCandidatePrices() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        List<PriceDto> today = priceService.getToday().stream()
                .filter(p -> p.getTimestamp().isAfter(nowUtc))
                .toList();
        List<PriceDto> tomorrow = priceService.getTomorrow();
        List<PriceDto> combined = new ArrayList<>(today);
        combined.addAll(tomorrow);
        return combined;
    }
}