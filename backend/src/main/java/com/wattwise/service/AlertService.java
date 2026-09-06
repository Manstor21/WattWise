package com.wattwise.service;

import com.wattwise.alert.AlertManager;
import com.wattwise.alert.PriceAlertContext;
import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.model.dto.AlertPreferenceDto;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.entity.AlertPreference;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.entity.User;
import com.wattwise.repository.AlertPreferenceRepository;
import com.wattwise.repository.ApplianceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * User-facing alert preferences + triggering. Alert evaluation itself is
 * delegated to {@link AlertManager} (Observer pattern); this service maps user
 * preferences to the observers' context.
 */
@Service
public class AlertService {

    private final AlertPreferenceRepository alertPreferenceRepository;
    private final ApplianceRepository applianceRepository;
    private final AlertManager alertManager;
    private final PriceService priceService;

    public AlertService(AlertPreferenceRepository alertPreferenceRepository,
                        ApplianceRepository applianceRepository,
                        AlertManager alertManager,
                        PriceService priceService) {
        this.alertPreferenceRepository = alertPreferenceRepository;
        this.applianceRepository = applianceRepository;
        this.alertManager = alertManager;
        this.priceService = priceService;
    }

    @Transactional(readOnly = true)
    public AlertPreferenceDto getPreferences(Long userId) {
        AlertPreference pref = getOrCreate(userId);
        return toDto(pref);
    }

    @Transactional
    public AlertPreferenceDto updatePreferences(Long userId, AlertPreferenceDto dto) {
        AlertPreference pref = getOrCreate(userId);
        if (dto.getApplianceId() != null) {
            Appliance appliance = applianceRepository.findById(dto.getApplianceId())
                    .filter(a -> a.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Appliance not found: " + dto.getApplianceId()));
            pref.setAppliance(appliance);
        }
        if (dto.getThresholdPctBelowMean() != null) {
            pref.setThresholdPctBelowMean(dto.getThresholdPctBelowMean());
        }
        if (dto.getIsActive() != null) {
            pref.setActive(dto.getIsActive());
        }
        return toDto(alertPreferenceRepository.save(pref));
    }

    /**
     * Evaluate today's prices against the user's alert preferences and return
     * the alerts that fired. Called by the scheduler after each price fetch and
     * exposed for on-demand triggering.
     */
    @Transactional
    public List<String> checkForUser(Long userId) {
        AlertPreference pref = getOrCreate(userId);
        if (!pref.isActive()) {
            return List.of();
        }
        List<PriceDto> today = priceService.getToday();
        if (today.isEmpty()) {
            return List.of();
        }
        BigDecimal mean = priceService.getTodayMeanEurPerKwh();
        PriceAlertContext context = new PriceAlertContext(today, mean, pref.getThresholdPctBelowMean());
        return alertManager.checkAndNotify(context);
    }

    /** Lazily create a default preference row so the profile endpoint never 404s. */
    private AlertPreference getOrCreate(Long userId) {
        return alertPreferenceRepository.findByUserId(userId).orElseGet(() -> {
            AlertPreference pref = new AlertPreference();
            User user = new User();
            user.setId(userId);
            pref.setUser(user);
            pref.setThresholdPctBelowMean(new BigDecimal("20.00"));
            pref.setActive(true);
            return alertPreferenceRepository.save(pref);
        });
    }

    private AlertPreferenceDto toDto(AlertPreference pref) {
        AlertPreferenceDto dto = new AlertPreferenceDto();
        dto.setId(pref.getId());
        dto.setApplianceId(pref.getAppliance() != null ? pref.getAppliance().getId() : null);
        dto.setThresholdPctBelowMean(pref.getThresholdPctBelowMean());
        dto.setIsActive(pref.isActive());
        dto.setNotifiedAt(pref.getNotifiedAt());
        dto.setCreatedAt(pref.getCreatedAt());
        return dto;
    }
}