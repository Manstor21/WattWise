package com.wattwise.android;

import android.app.Application;

import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.notification.NotificationHelper;
import com.wattwise.android.notification.PriceCheckWorker;

/**
 * Punto de entrada de la aplicación: inicializa el cliente Retrofit con la
 * sesión actual (token disponible para el interceptor de autenticación antes de
 * cualquier petición), crea el canal de notificación y programa el worker de
 * comprobación de precios cada 6 horas.
 */
public class WattWiseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ApiClient.init(new SessionManager(this));
        NotificationHelper.createChannel(this);
        PriceCheckWorker.schedule(this);
    }
}