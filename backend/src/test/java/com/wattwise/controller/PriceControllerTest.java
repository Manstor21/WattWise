package com.wattwise.controller;

import com.wattwise.config.SecurityConfig;
import com.wattwise.model.dto.PriceDto;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.model.enums.TrafficLight;
import com.wattwise.security.JwtAuthenticationFilter;
import com.wattwise.service.PriceService;
import com.wattwise.testutil.TestSecurityConfig;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PriceController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)})
@Import(TestSecurityConfig.class)
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PriceService priceService;

    private PriceDto greenSlot() {
        return new PriceDto(1L, LocalDateTime.of(2025, 1, 5, 0, 0),
                new BigDecimal("0.10"), null, new BigDecimal("0.10"),
                PriceSource.ESIOS, TrafficLight.GREEN);
    }

    @Test
    void todayReturnsClassifiedSlots() throws Exception {
        when(priceService.getToday()).thenReturn(List.of(greenSlot()));

        mockMvc.perform(get("/api/prices/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].color").value("GREEN"))
                .andExpect(jsonPath("$[0].priceEurPerKwh").value(0.10))
                .andExpect(jsonPath("$[0].source").value("ESIOS"));
    }

    @Test
    void tomorrowReturnsSlotsWhenPublished() throws Exception {
        when(priceService.getTomorrow()).thenReturn(List.of(greenSlot()));

        mockMvc.perform(get("/api/prices/tomorrow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp").value("2025-01-05T00:00:00"));
    }

    @Test
    void rangeRequiresFromAndTo() throws Exception {
        when(priceService.getRange(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/prices/range")
                        .param("from", "2025-01-05")
                        .param("to", "2025-01-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rangeRejectsMissingParameter() throws Exception {
        mockMvc.perform(get("/api/prices/range")
                        .param("from", "2025-01-05"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeRejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/prices/range")
                        .param("from", "05-01-2025")
                        .param("to", "2025-01-06"))
                .andExpect(status().isBadRequest());
    }
}