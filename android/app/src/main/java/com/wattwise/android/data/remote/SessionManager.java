package com.wattwise.android.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * JWT e identidad de usuario almacenados en un {@link EncryptedSharedPreferences}
 * respaldado por una clave maestra de Android Keystore. Si el Keystore no está
 * disponible (casos límite de root/emulador), un {@link SharedPreferences}
 * plano como respaldo asegura que la app siga funcionando — seguridad
 * degradada, no un cierre.
 */
public final class SessionManager {

    private static final String PREF_NAME = "wattwise_session";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = buildPrefs(context);
    }

    private static SharedPreferences buildPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Keystore no disponible en este dispositivo — se vuelve al almacenamiento
            // plano para que la app sea utilizable (el JWT seguiría viajando en
            // la cabecera HTTP en claro).
            return context.getSharedPreferences(PREF_NAME + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public boolean hasSession() {
        return !TextUtils.isEmpty(getToken());
    }

    public void saveSession(String token, long userId, String username, String email) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}