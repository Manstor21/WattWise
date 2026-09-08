package com.wattwise.service;

import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.model.dto.ApplianceDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.entity.User;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.repository.ApplianceRepository;
import com.wattwise.repository.AlertPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CRUD for user-owned appliances. All operations are scoped to the authenticated
 * user; an appliance belonging to another user is treated as not found (no data
 * leakage).
 */
@Service
public class ApplianceService {

    private final ApplianceRepository applianceRepository;
    private final AlertPreferenceRepository alertPreferenceRepository;

    public ApplianceService(ApplianceRepository applianceRepository,
                            AlertPreferenceRepository alertPreferenceRepository) {
        this.applianceRepository = applianceRepository;
        this.alertPreferenceRepository = alertPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<Appliance> getAppliances(Long userId) {
        return applianceRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    @Transactional(readOnly = true)
    public Appliance getApplianceForUser(Long applianceId, Long userId) {
        return applianceRepository.findById(applianceId)
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Appliance not found: " + applianceId));
    }

    @Transactional
    public Appliance create(Long userId, ApplianceDto dto) {
        User user = new User();
        user.setId(userId);

        Appliance appliance = new Appliance();
        appliance.setUser(user);
        apply(dto, appliance);
        return applianceRepository.save(appliance);
    }

    @Transactional
    public Appliance update(Long applianceId, Long userId, ApplianceDto dto) {
        Appliance appliance = getApplianceForUser(applianceId, userId);
        if (dto.getName() != null) appliance.setName(dto.getName());
        if (dto.getType() != null) appliance.setType(dto.getType());
        if (dto.getPowerWatts() != null) appliance.setPowerWatts(dto.getPowerWatts());
        if (dto.getAvgCycleKwh() != null) appliance.setAvgCycleKwh(dto.getAvgCycleKwh());
        if (dto.getEstimatedCycleMinutes() != null) appliance.setEstimatedCycleMinutes(dto.getEstimatedCycleMinutes());
        if (dto.getIsActive() != null) appliance.setActive(dto.getIsActive());
        return applianceRepository.save(appliance);
    }

    @Transactional
    public void delete(Long applianceId, Long userId) {
        Appliance appliance = getApplianceForUser(applianceId, userId);
        // V1 uses ON DELETE NO ACTION for fk_alert_pref_appliance (SQL Server
        // forbids multiple cascade paths), so drop affected alerts first.
        alertPreferenceRepository.deleteByApplianceId(applianceId);
        applianceRepository.delete(appliance);
    }

    // ------------------------------------------------------------------
    // DTO views (controllers never see entities)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ApplianceDto> getAppliancesDto(Long userId) {
        return getAppliances(userId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ApplianceDto getApplianceDto(Long applianceId, Long userId) {
        return toDto(getApplianceForUser(applianceId, userId));
    }

    @Transactional
    public ApplianceDto createDto(Long userId, ApplianceDto dto) {
        return toDto(create(userId, dto));
    }

    @Transactional
    public ApplianceDto updateDto(Long applianceId, Long userId, ApplianceDto dto) {
        return toDto(update(applianceId, userId, dto));
    }

    public ApplianceDto toDto(Appliance appliance) {
        ApplianceDto dto = new ApplianceDto();
        dto.setId(appliance.getId());
        dto.setName(appliance.getName());
        dto.setType(appliance.getType());
        dto.setPowerWatts(appliance.getPowerWatts());
        dto.setAvgCycleKwh(appliance.getAvgCycleKwh());
        dto.setEstimatedCycleMinutes(appliance.getEstimatedCycleMinutes());
        dto.setIsActive(appliance.isActive());
        dto.setCreatedAt(appliance.getCreatedAt());
        return dto;
    }

    /** Applies catalog defaults when the user omits consumption values. */
    private void apply(ApplianceDto dto, Appliance appliance) {
        appliance.setName(dto.getName());
        appliance.setType(dto.getType());
        appliance.setPowerWatts(dto.getPowerWatts() != null
                ? dto.getPowerWatts()
                : catalogPowerWatts(dto.getType()));
        appliance.setAvgCycleKwh(dto.getAvgCycleKwh() != null
                ? dto.getAvgCycleKwh()
                : catalogCycleKwh(dto.getType()));
        appliance.setEstimatedCycleMinutes(dto.getEstimatedCycleMinutes() != null
                ? dto.getEstimatedCycleMinutes()
                : catalogCycleMinutes(dto.getType()));
        appliance.setActive(dto.getIsActive() == null || dto.getIsActive());
    }

    /**
     * Embedded catalog of typical consumption per appliance type (mirrors the
     * V2__seed_catalog.sql values). Kept in Java so the dev profile (SQLite,
     * no Flyway, ddl-auto=update) still gets sensible defaults.
     */
    public static Integer catalogPowerWatts(ApplianceType type) {
        return switch (type) {
            case WASHING_MACHINE -> 2000;
            case DISHWASHER -> 1800;
            case EV_CHARGER -> 7400;
            case DRYER -> 2500;
            case POOL_PUMP -> 1100;
            case AC -> 2200;
            case OTHER -> 1500;
        };
    }

    public static BigDecimal catalogCycleKwh(ApplianceType type) {
        return switch (type) {
            case WASHING_MACHINE -> new BigDecimal("1.000");
            case DISHWASHER -> new BigDecimal("0.900");
            case EV_CHARGER -> new BigDecimal("7.000");
            case DRYER -> new BigDecimal("2.000");
            case POOL_PUMP -> new BigDecimal("5.000");
            case AC -> new BigDecimal("1.500");
            case OTHER -> new BigDecimal("1.200");
        };
    }

    public static int catalogCycleMinutes(ApplianceType type) {
        return switch (type) {
            case WASHING_MACHINE -> 120;
            case DISHWASHER -> 90;
            case EV_CHARGER -> 240;
            case DRYER -> 90;
            case POOL_PUMP -> 300;
            case AC -> 120;
            case OTHER -> 90;
        };
    }
}