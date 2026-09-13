package com.wattwise.controller;

import com.wattwise.config.SecurityConfig;
import com.wattwise.exception.PushNotConfiguredException;
import com.wattwise.security.JwtAuthenticationFilter;
import com.wattwise.service.PushSenderService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PushController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)})
@Import(TestSecurityConfig.class)
class PushControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushSenderService pushSenderService;

    @Test
    @WithMockPrincipal(role = "ADMIN")
    void sendWithoutTargetBroadcastsToAll() throws Exception {
        when(pushSenderService.sendToAll("Aviso", "Picos mañana")).thenReturn(3);

        mockMvc.perform(post("/api/push/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Aviso","body":"Picos mañana"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").value(3));

        verify(pushSenderService).sendToAll("Aviso", "Picos mañana");
        verify(pushSenderService, never()).sendToUser(anyLong(), any(), any());
        verify(pushSenderService, never()).sendToToken(any(), any(), any());
    }

    @Test
    @WithMockPrincipal(role = "ADMIN")
    void sendWithUserIdTargetsThatUser() throws Exception {
        when(pushSenderService.sendToUser(7L, "Aviso", "Picos mañana")).thenReturn(2);

        mockMvc.perform(post("/api/push/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Aviso","body":"Picos mañana","userId":7}
                                """))
                .andExpect(status().isOk());

        verify(pushSenderService).sendToUser(7L, "Aviso", "Picos mañana");
    }

    @Test
    @WithMockPrincipal(role = "ADMIN")
    void sendWithTokenTargetsThatDevice() throws Exception {
        when(pushSenderService.sendToToken("fcm-device", "Aviso", null)).thenReturn(1);

        mockMvc.perform(post("/api/push/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Aviso","token":"fcm-device"}
                                """))
                .andExpect(status().isOk());

        verify(pushSenderService).sendToToken("fcm-device", "Aviso", null);
    }

    @Test
    @WithMockPrincipal(role = "ADMIN")
    void sendRejectsMissingTitle() throws Exception {
        mockMvc.perform(post("/api/push/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"sin titulo"}
                                """))
                .andExpect(status().isBadRequest());

        verify(pushSenderService, never()).sendToAll(any(), any());
    }

    @Test
    @WithMockPrincipal(role = "ADMIN")
    void sendReturns503WhenFirebaseNotConfigured() throws Exception {
        when(pushSenderService.sendToAll(any(), any()))
                .thenThrow(new PushNotConfiguredException("Push no disponible: no hay credenciales Firebase"));

        mockMvc.perform(post("/api/push/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Aviso"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}