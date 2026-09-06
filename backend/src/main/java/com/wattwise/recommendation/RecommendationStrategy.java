package com.wattwise.recommendation;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.dto.RecommendationDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;

import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern: one implementation per appliance type. Each strategy
 * translates the traffic-light context into an appliance-specific scheduling
 * recommendation (optimal window within the appliance's constraints).
 *
 * @see StrategyFactory
 */
public interface RecommendationStrategy {

    /** The appliance type this strategy serves. Must be unique per implementation. */
    ApplianceType supportedType();

    /**
     * Whether this strategy handles {@code appliance}. Default: matches the
     * declared {@link #supportedType()}. Multi-type strategies override this.
     */
    default boolean appliesTo(Appliance appliance) {
        return supportedType() == appliance.getType();
    }

    /**
     * Compute the best contiguous price window for {@code appliance}.
     *
     * @param appliance the user's appliance
     * @param prices    classified future price slots (chronological)
     * @return the recommendation, or empty when there is not enough data
     */
    Optional<RecommendationDto> recommend(Appliance appliance, List<PriceDto> prices);
}