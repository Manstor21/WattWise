package com.wattwise.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.wattwise.exception.PushNotConfiguredException;
import com.wattwise.model.entity.PushToken;
import com.wattwise.repository.PushTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Envío de notificaciones push vía Firebase Admin SDK. Si la infraestructura
 * Firebase no está configurada (dev/test sin credenciales) todos los métodos
 * lanzan {@link PushNotConfiguredException} — mapeada a HTTP 503 — y el arranque
 * de Spring sigue funcionando (ver {@link com.wattwise.config.FirebaseAdminConfig}).
 *
 * <p>Los fallos por token (token obsoleto, límite de cuota, etc.) se registran y
 * no abortan el resto del envío; los llamantes reciben cuántos dispositivos
 * aceptaron la notificación.
 */
@Service
public class PushSenderService {

    private static final Logger log = LoggerFactory.getLogger(PushSenderService.class);

    private final PushTokenRepository pushTokenRepository;
    private final Optional<FirebaseMessaging> firebaseMessaging;

    public PushSenderService(PushTokenRepository pushTokenRepository,
                             Optional<FirebaseMessaging> firebaseMessaging) {
        this.pushTokenRepository = pushTokenRepository;
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Envía a todos los dispositivos registrados por todos los usuarios.
     *
     * @return número de dispositivos que aceptaron la notificación
     */
    public int sendToAll(String title, String body) {
        List<PushToken> all = pushTokenRepository.findAll();
        return sendToTokens(all.stream().map(PushToken::getToken).toList(), title, body);
    }

    /**
     * Envía a todos los dispositivos registrados de un usuario concreto.
     *
     * @return número de dispositivos que aceptaron la notificación
     */
    public int sendToUser(Long userId, String title, String body) {
        List<PushToken> tokens = pushTokenRepository.findByUserId(userId);
        return sendToTokens(tokens.stream().map(PushToken::getToken).toList(), title, body);
    }

    /**
     * Envía a un único token de dispositivo.
     *
     * @return 1 si el token aceptó la notificación, 0 si fue rechazado
     */
    public int sendToToken(String token, String title, String body) {
        return sendToTokens(List.of(token), title, body);
    }

    private int sendToTokens(List<String> tokens, String title, String body) {
        FirebaseMessaging messaging = requireMessaging();
        int sent = 0;
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .build();
                messaging.send(message);
                sent++;
            } catch (FirebaseMessagingException e) {
                // Token obsoleto/cuota/etc.: se registra y se continúa con el siguiente.
                log.warn("Fallo push al token {}: {}", token, e.getMessage());
            }
        }
        return sent;
    }

    private FirebaseMessaging requireMessaging() {
        return firebaseMessaging.orElseThrow(() -> new PushNotConfiguredException(
                "Push no disponible: no hay credenciales Firebase configuradas "
                        + "(FIREBASE_SERVICE_ACCOUNT_PATH / FIREBASE_PROJECT_ID)"));
    }
}