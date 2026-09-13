package com.wattwise.android.fcm;

import android.content.Context;
import android.util.Log;

import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.SessionManager;
import com.wattwise.android.data.remote.dto.RegisterPushTokenRequest;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Prefs;

import java.io.IOException;

import retrofit2.Response;

/**
 * Registro del token FCM en el backend, lanzado-y-olvidado en el pool de hilos
 * de {@link AppExecutors}. Si aún no hay sesión (token recibido antes del
 * login), el token se guarda como pendiente y se envía en el siguiente login
 * mediante {@link #registerPending}.
 */
public final class FcmTokenRegistrar {

    private static final String TAG = "FcmTokenRegistrar";

    private FcmTokenRegistrar() {
    }

    /** Encola el registro de {@code token}; si no hay sesión, lo deja pendiente. */
    public static void register(Context context, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        AppExecutors.io(() -> registerBlocking(context.getApplicationContext(), token));
    }

    /** Envía el token FCM pendiente de una sesión anterior (se llama tras el login). */
    public static void registerPending(Context context) {
        String pending = new Prefs(context.getApplicationContext()).pendingFcmToken();
        if (pending != null && !pending.isEmpty()) {
            register(context, pending);
        }
    }

    private static void registerBlocking(Context ctx, String token) {
        Prefs prefs = new Prefs(ctx);
        SessionManager session = new SessionManager(ctx);
        if (!session.hasSession()) {
            prefs.setPendingFcmToken(token);
            return;
        }
        try {
            Response<Void> response = ApiClient.api()
                    .registerPushToken(new RegisterPushTokenRequest(token, "ANDROID"))
                    .execute();
            if (response.isSuccessful()) {
                prefs.clearPendingFcmToken();
                Log.d(TAG, "Token FCM registrado en el backend");
            } else if (response.code() == 401 || response.code() == 403) {
                // Sesión caducada: el token vuelve a quedar pendiente hasta el próximo login.
                session.clear();
                prefs.setPendingFcmToken(token);
            } else {
                Log.w(TAG, "Registro de token FCM rechazado: HTTP " + response.code());
            }
        } catch (IOException e) {
            // Red caída; onNewToken volverá a invocarse en el siguiente lanzamiento
            // y registerPending tras el login re-intenta también.
            Log.w(TAG, "No se pudo registrar el token FCM: " + e.getMessage());
        }
    }
}