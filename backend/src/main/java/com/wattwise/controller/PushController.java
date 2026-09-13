package com.wattwise.controller;

import com.wattwise.model.dto.PushSendRequest;
import com.wattwise.model.dto.PushSendResponse;
import com.wattwise.service.PushSenderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Envío de notificaciones push a dispositivos registrados. Restringido a ADMIN
 * vía {@link PreAuthorize}: la seguridad de llamada a métodos ya está habilitada
 * por {@link com.wattwise.config.SecurityConfig} (@EnableMethodSecurity).
 */
@RestController
@RequestMapping("/api/push")
@Tag(name = "Push", description = "Envío de notificaciones push a dispositivos (solo ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class PushController {

    private final PushSenderService pushSenderService;

    public PushController(PushSenderService pushSenderService) {
        this.pushSenderService = pushSenderService;
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar una notificación push",
            description = "Envía a todos los dispositivos registrados por defecto. "
                    + "Se puede acotar el destino con 'token' (un dispositivo concreto) "
                    + "o con 'userId' (todos los dispositivos del usuario). Precedencia: "
                    + "token > userId > todos. Requiere el Admin SDK de Firebase configurado "
                    + "(FIREBASE_SERVICE_ACCOUNT_PATH); sin credenciales responde 503.")
    public PushSendResponse send(@Valid @RequestBody PushSendRequest request) {
        String title = request.getTitle();
        String body = request.getBody();
        if (request.getToken() != null && !request.getToken().isBlank()) {
            return new PushSendResponse(pushSenderService.sendToToken(request.getToken().trim(), title, body));
        }
        if (request.getUserId() != null) {
            return new PushSendResponse(pushSenderService.sendToUser(request.getUserId(), title, body));
        }
        return new PushSendResponse(pushSenderService.sendToAll(title, body));
    }
}