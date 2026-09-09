package com.wattwise.service;

import com.wattwise.alert.AlertManager;
import com.wattwise.alert.AnomalyObserver;
import com.wattwise.alert.PriceThresholdObserver;
import com.wattwise.model.dto.AlertPreferenceDto;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.entity.AlertPreference;
import com.wattwise.model.entity.User;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.repository.AlertPreferenceRepository;
import com.wattwise.repository.ApplianceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertPreferenceRepository alertPreferenceRepository;

    @Mock
    private ApplianceRepository applianceRepository;

    @Mock
    private PriceService priceService;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        // Observadores reales conectados a través del manager real (patrón Observer de extremo a extremo).
        AlertManager manager = new AlertManager(List.of(
                new PriceThresholdObserver(),
                new AnomalyObserver()));
        alertService = new AlertService(alertPreferenceRepository, applianceRepository,
                manager, priceService);
    }

    private PriceDto price(int i, double total, TrafficLight color) {
        return new PriceDto((long) i, LocalDateTime.of(2099, 6, 1, 0, 0).plusMinutes(i * 15L),
                BigDecimal.valueOf(total), null, BigDecimal.valueOf(total), PriceSource.ESIOS, color);
    }

    private AlertPreference activePreference(Long id, Long userId, String threshold) {
        AlertPreference pref = new AlertPreference();
        setField(pref, "id", id);
        User u = new User();
        u.setId(userId);
        pref.setUser(u);
        pref.setThresholdPctBelowMean(new BigDecimal(threshold));
        pref.setActive(true);
        return pref;
    }

    @Test
    void getPreferencesCreatesDefaultWhenNoneExists() {
        when(alertPreferenceRepository.findByUserId(5L)).thenReturn(Optional.empty());
        when(alertPreferenceRepository.save(any(AlertPreference.class))).thenAnswer(inv -> {
            AlertPreference p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        AlertPreferenceDto dto = alertService.getPreferences(5L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getThresholdPctBelowMean()).isEqualByComparingTo("20.00");
        assertThat(dto.getIsActive()).isTrue();
    }

    @Test
    void getPreferencesReturnsExistingWhenPresent() {
        when(alertPreferenceRepository.findByUserId(5L))
                .thenReturn(Optional.of(activePreference(7L, 5L, "15.00")));

        AlertPreferenceDto dto = alertService.getPreferences(5L);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getThresholdPctBelowMean()).isEqualByComparingTo("15.00");
    }

    @Test
    void checkForUserFiresThresholdObserverWhenWindowIsVeryCheap() {
        when(alertPreferenceRepository.findByUserId(5L))
                .thenReturn(Optional.of(activePreference(7L, 5L, "20.00")));
        // Media 0.30; slot más barato 0.18 => 40% por debajo de la media -> el umbral (20%) se dispara.
        when(priceService.getToday()).thenReturn(List.of(
                price(0, 0.40, TrafficLight.RED),
                price(1, 0.32, TrafficLight.AMBER),
                price(2, 0.18, TrafficLight.GREEN)));
        when(priceService.getTodayMeanEurPerKwh()).thenReturn(new BigDecimal("0.30"));

        List<String> alerts = alertService.checkForUser(5L);

        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0)).contains("PRICE_THRESHOLD");
    }

    @Test
    void checkForUserDoesNotFireWhenPricesAreUniform() {
        when(alertPreferenceRepository.findByUserId(5L))
                .thenReturn(Optional.of(activePreference(7L, 5L, "20.00")));
        // Precios idénticos a la media -> no hay franja barata.
        when(priceService.getToday()).thenReturn(List.of(
                price(0, 0.30, TrafficLight.AMBER),
                price(1, 0.30, TrafficLight.AMBER)));
        when(priceService.getTodayMeanEurPerKwh()).thenReturn(new BigDecimal("0.30"));

        List<String> alerts = alertService.checkForUser(5L);

        assertThat(alerts).isEmpty();
    }

    @Test
    void checkForUserDoesNotFireWhenPreferenceInactive() {
        AlertPreference inactive = activePreference(7L, 5L, "20.00");
        inactive.setActive(false);
        when(alertPreferenceRepository.findByUserId(5L)).thenReturn(Optional.of(inactive));

        List<String> alerts = alertService.checkForUser(5L);

        assertThat(alerts).isEmpty();
    }

    @Test
    void checkForUserReturnsEmptyWhenNoPricesToday() {
        when(alertPreferenceRepository.findByUserId(5L))
                .thenReturn(Optional.of(activePreference(7L, 5L, "20.00")));
        when(priceService.getToday()).thenReturn(List.of());

        List<String> alerts = alertService.checkForUser(5L);

        assertThat(alerts).isEmpty();
    }

    @Test
    void anomalyObserverFiresWhenPriceExceedsDoubleMean() {
        when(alertPreferenceRepository.findByUserId(5L))
                .thenReturn(Optional.of(activePreference(7L, 5L, "75.00")));
        // Media real 0.333334; el slot de 0.80 supera 2x la media (0.666668) -> ANOMALY
        // se dispara, mientras que el umbral (75% por debajo de la media = 0.0833) no.
        when(priceService.getToday()).thenReturn(List.of(
                price(0, 0.10, TrafficLight.GREEN),
                price(1, 0.10, TrafficLight.GREEN),
                price(2, 0.80, TrafficLight.RED)));
        when(priceService.getTodayMeanEurPerKwh()).thenReturn(new BigDecimal("0.333334"));

        List<String> alerts = alertService.checkForUser(5L);

        assertThat(alerts).containsExactly(
                "ANOMALY — Price anomaly detected: at least one slot exceeds 2× today's mean");
    }
}