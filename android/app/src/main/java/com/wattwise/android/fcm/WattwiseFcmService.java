package com.wattwise.android.fcm;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.wattwise.android.R;
import com.wattwise.android.notification.NotificationHelper;

/**
 * Receptor de notificaciones push de Firebase Cloud Messaging. El backend
 * envía un título y un cuerpo (payload {@code notification}); este servicio
 * los muestra por el canal de {@link NotificationHelper} y, cuando Firebase
 * renueva el token del dispositivo, lo registra en el backend.
 */
public class WattwiseFcmService extends FirebaseMessagingService {

    private static final String TAG = "WattwiseFcm";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        RemoteMessage.Notification notification = message.getNotification();
        String title = notification != null ? notification.getTitle() : message.getData().get("title");
        String body = notification != null ? notification.getBody() : message.getData().get("body");
        if (title == null) {
            title = getString(R.string.notification_push_default_title);
        }
        if (body == null) {
            body = getString(R.string.notification_push_default_body);
        }
        NotificationHelper.notifyPush(this, title, body);
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "Token FCM renovado; se registra en el backend");
        FcmTokenRegistrar.register(this, token);
    }
}