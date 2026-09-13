package com.wattwise.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Configuración del Admin SDK de Firebase para envío de notificaciones push.
 * Las propiedades vienen de {@code wattwise.firebase.*} (application.yml):
 * {@code service-account-json-path} y {@code project-id}.
 *
 * <p><b>Inicialización condicional/lazy:</b> el arranque del backend nunca
 * depende de Firebase. Si {@code service-account-json-path} está vacío (desarrollo
 * y tests sin credenciales), el bean {@link FirebaseMessaging} no se crea y se
 * expone un {@link Optional} vacío; {@link com.wattwise.service.PushSenderService}
 * responde entonces con un 503 claro en lugar de fallar el arranque. Solo cuando
 * se define la ruta se inicializa {@link FirebaseApp} con el service account.
 */
@Component
@ConfigurationProperties(prefix = "wattwise.firebase")
public class FirebaseAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    private String serviceAccountJsonPath = "";
    private String projectId = "";

    public String getServiceAccountJsonPath() {
        return serviceAccountJsonPath;
    }

    public void setServiceAccountJsonPath(String serviceAccountJsonPath) {
        this.serviceAccountJsonPath = serviceAccountJsonPath;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * Expone {@link FirebaseMessaging} de forma condicional. Sin ruta de service
     * account devuelve un {@link Optional} vacío (módulo push deshabilitado); con
     * ruta, inicializa la app Firebase por defecto y devuelve el cliente de
     * mensajería.
     */
    @Bean
    public Optional<FirebaseMessaging> firebaseMessaging() {
        if (serviceAccountJsonPath == null || serviceAccountJsonPath.isBlank()) {
            log.warn("Firebase no configurado: el push FCM queda deshabilitado. "
                    + "Define wattwise.firebase.service-account-json-path "
                    + "(env FIREBASE_SERVICE_ACCOUNT_PATH) para activarlo.");
            return Optional.empty();
        }
        FirebaseApp app = defaultAppOrInitialize();
        return Optional.of(FirebaseMessaging.getInstance(app));
    }

    private FirebaseApp defaultAppOrInitialize() {
        // Reutiliza la app "DEFAULT" si ya existe (p. ej. tests que la inicializaron antes).
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(app.getName())) {
                return app;
            }
        }
        try (FileInputStream stream = new FileInputStream(serviceAccountJsonPath)) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream));
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }
            log.info("Firebase inicializado con el service account '{}'", serviceAccountJsonPath);
            return FirebaseApp.initializeApp(builder.build());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo cargar el service account de Firebase desde '" + serviceAccountJsonPath + "'", e);
        }
    }
}