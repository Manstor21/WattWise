package com.wattwise.service;

import com.wattwise.exception.ResourceNotFoundException;
import com.wattwise.model.dto.ApplianceDto;
import com.wattwise.model.entity.Appliance;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.repository.ApplianceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplianceServiceTest {

    @Mock
    private ApplianceRepository applianceRepository;

    private ApplianceService applianceService;

    @BeforeEach
    void setUp() {
        applianceService = new ApplianceService(applianceRepository);
    }

    private ApplianceDto dto(String name, ApplianceType type) {
        ApplianceDto d = new ApplianceDto();
        d.setName(name);
        d.setType(type);
        return d;
    }

    private Appliance ownedAppliance(Long id, Long userId) {
        Appliance a = new Appliance();
        a.setId(id);
        com.wattwise.model.entity.User u = new com.wattwise.model.entity.User();
        u.setId(userId);
        a.setUser(u);
        a.setName("Lavadora");
        a.setType(ApplianceType.WASHING_MACHINE);
        a.setPowerWatts(2000);
        a.setAvgCycleKwh(new BigDecimal("1.000"));
        a.setEstimatedCycleMinutes(120);
        return a;
    }

    @Test
    void createAppliesCatalogDefaultsWhenOmitted() {
        ApplianceDto request = dto("Lavadora", ApplianceType.WASHING_MACHINE);
        when(applianceRepository.save(any(Appliance.class))).thenAnswer(inv -> {
            Appliance a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        Appliance saved = applianceService.create(5L, request);

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getUser().getId()).isEqualTo(5L);
        // Catalog defaults from V2 seed:
        assertThat(saved.getPowerWatts()).isEqualTo(2000);
        assertThat(saved.getAvgCycleKwh()).isEqualByComparingTo("1.000");
        assertThat(saved.getEstimatedCycleMinutes()).isEqualTo(120);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void createHonorsUserProvidedConsumption() {
        ApplianceDto request = dto("Cargador", ApplianceType.EV_CHARGER);
        request.setPowerWatts(5000);
        request.setAvgCycleKwh(new BigDecimal("6.500"));
        request.setEstimatedCycleMinutes(180);
        request.setIsActive(false);
        when(applianceRepository.save(any(Appliance.class))).thenAnswer(inv -> inv.getArgument(0));

        Appliance saved = applianceService.create(5L, request);

        assertThat(saved.getPowerWatts()).isEqualTo(5000);
        assertThat(saved.getAvgCycleKwh()).isEqualByComparingTo("6.500");
        assertThat(saved.getEstimatedCycleMinutes()).isEqualTo(180);
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void getApplianceForUserFiltersByOwner() {
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(ownedAppliance(1L, 5L)));

        Appliance found = applianceService.getApplianceForUser(1L, 5L);
        assertThat(found.getId()).isEqualTo(1L);

        // Same appliance, different owner → treated as not found.
        assertThatThrownBy(() -> applianceService.getApplianceForUser(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateModifiesOnlyProvidedFields() {
        Appliance existing = ownedAppliance(1L, 5L);
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(applianceRepository.save(any(Appliance.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplianceDto update = new ApplianceDto();
        update.setName("Lavadora Bosch");

        Appliance result = applianceService.update(1L, 5L, update);

        assertThat(result.getName()).isEqualTo("Lavadora Bosch");
        assertThat(result.getPowerWatts()).isEqualTo(2000); // untouched
    }

    @Test
    void updateRejectsOtherUsersAppliance() {
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(ownedAppliance(1L, 5L)));

        assertThatThrownBy(() -> applianceService.update(1L, 99L, new ApplianceDto()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesAppliance() {
        when(applianceRepository.findById(1L)).thenReturn(Optional.of(ownedAppliance(1L, 5L)));

        applianceService.delete(1L, 5L);

        verify(applianceRepository).delete(any(Appliance.class));
    }

    @Test
    void listReturnsOnlyUsersAppliances() {
        when(applianceRepository.findByUserIdOrderByCreatedAtAsc(5L))
                .thenReturn(List.of(ownedAppliance(1L, 5L)));

        List<ApplianceDto> dtos = applianceService.getAppliancesDto(5L);

        assertThat(dtos).hasSize(1);
        ApplianceDto d = dtos.get(0);
        assertThat(d.getId()).isEqualTo(1L);
        assertThat(d.getType()).isEqualTo(ApplianceType.WASHING_MACHINE);
    }
}