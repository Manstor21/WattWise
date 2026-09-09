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
 * Preferencias de alerta orientadas al usuario + disparo. La evaluación de alertas en sí
 * se delega en {@link AlertManager} (patrón Observer); este service asigna las preferencias
 * del usuario al contexto de los observers.
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
     * Evalúa los precios de hoy frente a las preferencias de alerta del usuario y devuelve
     * las alertas disparadas. Lo llama el scheduler tras cada descarga de precios y
     * también se expone para disparo bajo demanda.
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

    /** Crea perezosamente una fila de preferencias por defecto para que el endpoint de perfil nunca devuelva 404. */
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