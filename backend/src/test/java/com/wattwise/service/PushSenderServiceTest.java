package com.wattwise.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.wattwise.exception.PushNotConfiguredException;
import com.wattwise.model.entity.PushToken;
import com.wattwise.model.entity.User;
import com.wattwise.repository.PushTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSenderServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private PushSenderService pushSenderService;

    @BeforeEach
    void setUp() {
        pushSenderService = new PushSenderService(pushTokenRepository, Optional.of(firebaseMessaging));
    }

    private PushToken token(String raw) {
        PushToken pushToken = new PushToken();
        pushToken.setToken(raw);
        User user = new User();
        user.setId(1L);
        pushToken.setUser(user);
        return pushToken;
    }

    @Test
    void sendToTokenDeliversNotification() throws FirebaseMessagingException {
        when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/wattwise/messages/123");

        int sent = pushSenderService.sendToToken("fcm-1", "Título", "Cuerpo");

        assertThat(sent).isEqualTo(1);
        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void sendToAllIteratesAllRegisteredTokens() throws FirebaseMessagingException {
        when(pushTokenRepository.findAll()).thenReturn(List.of(token("fcm-1"), token("fcm-2"), token("fcm-3")));

        int sent = pushSenderService.sendToAll("Aviso", "Picos mañana");

        assertThat(sent).isEqualTo(3);
        verify(firebaseMessaging, times(3)).send(any(Message.class));
    }

    @Test
    void sendToUserOnlyIteratesThatUsersTokens() throws FirebaseMessagingException {
        when(pushTokenRepository.findByUserId(7L)).thenReturn(List.of(token("fcm-a"), token("fcm-b")));

        int sent = pushSenderService.sendToUser(7L, "Aviso", "Ventana barata");

        assertThat(sent).isEqualTo(2);
        verify(firebaseMessaging, times(2)).send(any(Message.class));
    }

    @Test
    void sendToTokensWithEmptyListDoesNotCallMessaging() throws FirebaseMessagingException {
        when(pushTokenRepository.findAll()).thenReturn(List.of());

        int sent = pushSenderService.sendToAll("Aviso", "Sin destinatarios");

        assertThat(sent).isZero();
        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    void sendContinuesAfterPerTokenFailure() throws FirebaseMessagingException {
        when(pushTokenRepository.findAll()).thenReturn(List.of(token("fcm-bad"), token("fcm-ok")));
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(firebaseMessaging.send(any(Message.class)))
                .thenThrow(ex)
                .thenReturn("id");

        int sent = pushSenderService.sendToAll("Aviso", "Alguno fallará");

        assertThat(sent).isEqualTo(1);
        verify(firebaseMessaging, times(2)).send(any(Message.class));
    }

    @Test
    void sendWithoutFirebaseConfigurationThrows() {
        PushSenderService unconfigured = new PushSenderService(pushTokenRepository, Optional.empty());

        assertThatThrownBy(() -> unconfigured.sendToAll("Aviso", "Sin Firebase"))
                .isInstanceOf(PushNotConfiguredException.class)
                .hasMessageContaining("FIREBASE_SERVICE_ACCOUNT_PATH");
        assertThatThrownBy(() -> unconfigured.sendToToken("fcm-1", "Aviso", "Sin Firebase"))
                .isInstanceOf(PushNotConfiguredException.class);
    }
}