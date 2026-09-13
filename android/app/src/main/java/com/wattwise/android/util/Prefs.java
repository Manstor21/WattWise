package com.wattwise.android.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Shared preferences ligeras (no secretas) para estado solo de UI, como los
 * timestamps de la última sincronización y el umbral de alerta configurado
 * localmente. Los secretos van en {@link com.wattwise.android.data.remote.SessionManager}.
 */
public final class Prefs {

    private static final String NAME = "wattwise_prefs";

    private static final String KEY_PRICE_LAST_UPDATED = "price_last_updated_ms";
    private static final String KEY_RECOMMENDATION_LAST_UPDATED = "recommendation_last_updated_ms";
    private static final String KEY_ALERT_THRESHOLD = "alert_threshold_pct";
    private static final String KEY_ALERTS_ENABLED = "alerts_enabled";
    private static final String KEY_ALERT_APPLIANCE_ID = "alert_appliance_id";
    private static final String KEY_LAST_NOTIFIED_WINDOW = "last_notified_window_start";
    private static final String KEY_NOTIFICATION_PERMISSION_REQUESTED = "notif_perm_requested";
    private static final String KEY_PENDING_FCM_TOKEN = "pending_fcm_token";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor ed() {
        return prefs.edit();
    }

    // ---- sincronización de precios ----

    public long priceLastUpdatedMs() {
        return prefs.getLong(KEY_PRICE_LAST_UPDATED, 0L);
    }

    public void setPriceLastUpdatedMs(long ms) {
        ed().putLong(KEY_PRICE_LAST_UPDATED, ms).apply();
    }

    public long recommendationLastUpdatedMs() {
        return prefs.getLong(KEY_RECOMMENDATION_LAST_UPDATED, 0L);
    }

    public void setRecommendationLastUpdatedMs(long ms) {
        ed().putLong(KEY_RECOMMENDATION_LAST_UPDATED, ms).apply();
    }

    // ---- configuración de alertas ----

    public int alertThresholdPct() {
        return prefs.getInt(KEY_ALERT_THRESHOLD, 20);
    }

    public void setAlertThresholdPct(int pct) {
        ed().putInt(KEY_ALERT_THRESHOLD, Math.max(0, Math.min(100, pct))).apply();
    }

    public boolean alertsEnabled() {
        return prefs.getBoolean(KEY_ALERTS_ENABLED, true);
    }

    public void setAlertsEnabled(boolean enabled) {
        ed().putBoolean(KEY_ALERTS_ENABLED, enabled).apply();
    }

    public long alertApplianceId() {
        return prefs.getLong(KEY_ALERT_APPLIANCE_ID, -1L);
    }

    public void setAlertApplianceId(long id) {
        ed().putLong(KEY_ALERT_APPLIANCE_ID, id).apply();
    }

    // ---- deduplicación de notificaciones ----

    public String lastNotifiedWindowStart() {
        return prefs.getString(KEY_LAST_NOTIFIED_WINDOW, "");
    }

    public void setLastNotifiedWindowStart(String isoUtc) {
        ed().putString(KEY_LAST_NOTIFIED_WINDOW, isoUtc).apply();
    }

    public boolean notificationPermissionRequested() {
        return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false);
    }

    public void setNotificationPermissionRequested(boolean done) {
        ed().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, done).apply();
    }

    // ---- token FCM pendiente ----

    /** Token FCM guardado cuando se recibió antes de que existiera una sesión activa. */
    public String pendingFcmToken() {
        return prefs.getString(KEY_PENDING_FCM_TOKEN, null);
    }

    public void setPendingFcmToken(String token) {
        ed().putString(KEY_PENDING_FCM_TOKEN, token).apply();
    }

    public void clearPendingFcmToken() {
        ed().remove(KEY_PENDING_FCM_TOKEN).apply();
    }
}