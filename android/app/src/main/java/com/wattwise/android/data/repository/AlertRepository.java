package com.wattwise.android.data.repository;

import com.wattwise.android.data.remote.ApiClient;
import com.wattwise.android.data.remote.ApiErrorParser;
import com.wattwise.android.data.remote.dto.AlertPreferenceDto;
import com.wattwise.android.util.AppExecutors;
import com.wattwise.android.util.Callback;

import retrofit2.Response;

/**
 * Cliente ligero para los endpoints de preferencias de alerta (requieren JWT).
 * Sin caché local: las preferencias viven como estado de la UI en
 * {@link com.wattwise.android.util.Prefs} para que el {@code PriceCheckWorker}
 * pueda evaluar alertas sin sesión.
 */
public class AlertRepository {

    public void fetchPreferences(Callback<AlertPreferenceDto> callback) {
        AppExecutors.io(() -> {
            try {
                Response<AlertPreferenceDto> r = ApiClient.api().getAlertPreferences().execute();
                if (r.isSuccessful() && r.body() != null) {
                    AppExecutors.main(() -> callback.onSuccess(r.body()));
                } else {
                    AppExecutors.main(() -> callback.onError(
                            ApiErrorParser.isAuthError(r), ApiErrorParser.message(r)));
                }
            } catch (Exception e) {
                AppExecutors.main(() -> callback.onError(false, "network"));
            }
        });
    }

    public void savePreferences(AlertPreferenceDto dto, Callback<AlertPreferenceDto> callback) {
        AppExecutors.io(() -> {
            try {
                Response<AlertPreferenceDto> r = ApiClient.api().updateAlertPreferences(dto).execute();
                if (r.isSuccessful() && r.body() != null) {
                    AppExecutors.main(() -> callback.onSuccess(r.body()));
                } else {
                    AppExecutors.main(() -> callback.onError(
                            ApiErrorParser.isAuthError(r), ApiErrorParser.message(r)));
                }
            } catch (Exception e) {
                AppExecutors.main(() -> callback.onError(false, "network"));
            }
        });
    }

    public void checkAlerts(Callback<java.util.List<String>> callback) {
        AppExecutors.io(() -> {
            try {
                Response<java.util.List<String>> r = ApiClient.api().checkAlerts().execute();
                if (r.isSuccessful() && r.body() != null) {
                    AppExecutors.main(() -> callback.onSuccess(r.body()));
                } else {
                    AppExecutors.main(() -> callback.onError(
                            ApiErrorParser.isAuthError(r), ApiErrorParser.message(r)));
                }
            } catch (Exception e) {
                AppExecutors.main(() -> callback.onError(false, "network"));
            }
        });
    }
}