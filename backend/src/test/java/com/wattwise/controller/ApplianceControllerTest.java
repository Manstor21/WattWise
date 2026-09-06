package com.wattwise.controller;

import com.wattwise.config.SecurityConfig;
import com.wattwise.model.dto.ApplianceDto;
import com.wattwise.model.enums.ApplianceType;
import com.wattwise.security.JwtAuthenticationFilter;
import com.wattwise.service.ApplianceService;
import com.wattwise.testutil.TestSecurityConfig;
import com.wattwise.testutil.WithMockPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApplianceController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)})
@Import(TestSecurityConfig.class)
class ApplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApplianceService applianceService;

    private ApplianceDto applianceDto(Long id) {
        ApplianceDto dto = new ApplianceDto();
        dto.setId(id);
        dto.setName("Washing Machine");
        dto.setType(ApplianceType.WASHING_MACHINE);
        dto.setPowerWatts(2000);
        dto.setAvgCycleKwh(new BigDecimal("1.000"));
        dto.setEstimatedCycleMinutes(120);
        dto.setIsActive(true);
        return dto;
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void listScopesToAuthenticatedUser() throws Exception {
        when(applianceService.getAppliancesDto(5L)).thenReturn(List.of(applianceDto(1L)));

        mockMvc.perform(get("/api/appliances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Washing Machine"));

        verify(applianceService).getAppliancesDto(5L);
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void getReturnsOneAppliance() throws Exception {
        when(applianceService.getApplianceDto(1L, 5L)).thenReturn(applianceDto(1L));

        mockMvc.perform(get("/api/appliances/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WASHING_MACHINE"));
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void createReturnsCreated() throws Exception {
        when(applianceService.createDto(eq(5L), any())).thenReturn(applianceDto(1L));

        mockMvc.perform(post("/api/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Washing Machine","type":"WASHING_MACHINE","powerWatts":2000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(applianceService).createDto(eq(5L), any());
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void createRejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/appliances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mystery"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void updateReturnsUpdatedDto() throws Exception {
        when(applianceService.updateDto(eq(1L), eq(5L), any())).thenReturn(applianceDto(1L));

        mockMvc.perform(put("/api/appliances/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Washing Machine","type":"WASHING_MACHINE","isActive":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));

        verify(applianceService).updateDto(eq(1L), eq(5L), any());
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/appliances/1"))
                .andExpect(status().isNoContent());

        verify(applianceService).delete(1L, 5L);
    }
}