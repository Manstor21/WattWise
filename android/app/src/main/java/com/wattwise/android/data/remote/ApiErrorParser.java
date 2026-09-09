package com.wattwise.android.data.remote;

import com.wattwise.android.data.remote.dto.ApiError;
import com.wattwise.android.util.GsonProvider;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Convierte respuestas de error de Retrofit al envoltorio {@code ApiError} del
 * backend y expone un único mensaje legible para el usuario.
 */
public final class ApiErrorParser {

    private ApiErrorParser() {
    }

    public static String message(Response<?> response) {
        try {
            ResponseBody errorBody = response.errorBody();
            if (errorBody != null) {
                String body = errorBody.string();
                ApiError error = GsonProvider.get().fromJson(body, ApiError.class);
                if (error != null && error.getMessage() != null && !error.getMessage().isEmpty()) {
                    return error.getMessage();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Se cae al mensaje HTTP genérico.
        }
        return "HTTP " + response.code();
    }

    public static boolean isAuthError(Response<?> response) {
        return response.code() == 401 || response.code() == 403;
    }
}