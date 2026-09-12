package com.wattwise.controller;

import com.wattwise.config.SecurityConfig;
import com.wattwise.exception.ValidationException;
import com.wattwise.model.dto.BacktestMonthlyDto;
import com.wattwise.model.dto.BacktestReportDto;
import com.wattwise.security.JwtAuthenticationFilter;
import com.wattwise.service.BacktestService;
import com.wattwise.testutil.TestSecurityConfig;
import com.wattwise.testutil.WithMockPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de {@link BacktestController}: respuesta válida, valor por defecto del
 * parámetro {@code months} y control de errores (400 ante meses fuera de rango).
 */
@WebMvcTest(controllers = BacktestController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)})
@Import(TestSecurityConfig.class)
class BacktestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BacktestService backtestService;

    private BacktestReportDto sampleReport() {
        BacktestReportDto report = new BacktestReportDto();
        report.setPeriodFrom(LocalDate.of(2025, 12, 1));
        report.setPeriodTo(LocalDate.of(2026, 8, 31));
        report.setDaysAnalyzed(274);
        report.setNaiveCostEur(new BigDecimal("840.1234"));
        report.setOptimizedCostEur(new BigDecimal("640.1234"));
        report.setSavingsEur(new BigDecimal("200.0000"));
        report.setSavingsPercentage(new BigDecimal("23.81"));
        report.setApplianceMix(List.of("Lavadora — 1,0", "Lavavajillas — 0,9"));
        report.setMonthlyBreakdown(List.of(
                new BacktestMonthlyDto("2025-12", BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                        BigDecimal.valueOf(20), new BigDecimal("20.00"))));
        report.setAssumptions(List.of("Supuesto A"));
        report.setDataSource("Dataset simulado");
        return report;
    }

    @Test
    @WithMockPrincipal(id = 1L)
    void reportReturnsBacktestSummaryWhenAuthenticated() throws Exception {
        when(backtestService.generateReport(6)).thenReturn(sampleReport());

        mockMvc.perform(get("/api/backtest/report").param("months", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodFrom").value("2025-12-01"))
                .andExpect(jsonPath("$.periodTo").value("2026-08-31"))
                .andExpect(jsonPath("$.daysAnalyzed").value(274))
                .andExpect(jsonPath("$.applianceMix", hasSize(2)))
                .andExpect(jsonPath("$.monthlyBreakdown[0].month").value("2025-12"))
                .andExpect(jsonPath("$.assumptions", hasSize(1)))
                .andExpect(jsonPath("$.dataSource").value("Dataset simulado"));

        verify(backtestService).generateReport(6);
    }

    @Test
    @WithMockPrincipal(id = 1L)
    void reportDefaultsToTwelveMonths() throws Exception {
        when(backtestService.generateReport(12)).thenReturn(sampleReport());

        mockMvc.perform(get("/api/backtest/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysAnalyzed").value(274));

        verify(backtestService).generateReport(12);
    }

    @Test
    @WithMockPrincipal(id = 1L)
    void reportReturnsBadRequestWhenMonthsOutOfRange() throws Exception {
        when(backtestService.generateReport(0))
                .thenThrow(new ValidationException("El parámetro 'months' debe ser mayor o igual a 1"));

        mockMvc.perform(get("/api/backtest/report").param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'months' debe ser mayor o igual a 1"));
    }
}