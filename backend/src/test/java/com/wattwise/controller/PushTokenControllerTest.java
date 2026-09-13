package com.wattwise.controller;

import com.wattwise.config.SecurityConfig;
import com.wattwise.model.dto.PushTokenDto;
import com.wattwise.security.JwtAuthenticationFilter;
import com.wattwise.service.PushTokenService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PushTokenController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)})
@Import(TestSecurityConfig.class)
class PushTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushTokenService pushTokenService;

    private PushTokenDto pushTokenDto() {
        PushTokenDto dto = new PushTokenDto();
        dto.setId(1L);
        dto.setToken("fcm-token");
        dto.setPlatform("ANDROID");
        dto.setCreatedAt(LocalDateTime.of(2026, 9, 10, 8, 0));
        return dto;
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void registerUsesAuthenticatedUserId() throws Exception {
        when(pushTokenService.register(eq(5L), any())).thenReturn(pushTokenDto());

        mockMvc.perform(post("/api/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"fcm-token","platform":"ANDROID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.token").value("fcm-token"))
                .andExpect(jsonPath("$.platform").value("ANDROID"));

        verify(pushTokenService).register(eq(5L), any());
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void registerRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"platform":"ANDROID"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void listReturnsUsersTokens() throws Exception {
        when(pushTokenService.listByUserId(5L)).thenReturn(List.of(pushTokenDto()));

        mockMvc.perform(get("/api/push-tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].platform").value("ANDROID"));

        verify(pushTokenService).listByUserId(5L);
    }

    @Test
    @WithMockPrincipal(id = 5L)
    void deleteScopedToUser() throws Exception {
        mockMvc.perform(delete("/api/push-tokens/fcm-token"))
                .andExpect(status().isNoContent());

        verify(pushTokenService).deleteByToken(5L, "fcm-token");
    }
}