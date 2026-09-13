package com.wattwise.config;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseAdminConfigTest {

    @Test
    void unconfiguredYieldsEmptyMessaging() {
        FirebaseAdminConfig config = new FirebaseAdminConfig();
        config.setServiceAccountJsonPath(" ");
        config.setProjectId("");

        Optional<?> messaging = config.firebaseMessaging();

        // Sin ruta de service account el arranque no debe fallar ni crear FirebaseApp.
        assertThat(messaging).isEmpty();
    }
}