package com.wattwise.android.data.remote;

import com.wattwise.android.data.remote.dto.ApiError;
import com.wattwise.android.util.GsonProvider;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Parses Retrofit error responses into the backend's {@code ApiError} envelope
 * and exposes a single human-readable message.
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
            // Fall through to the generic HTTP message.
        }
        return "HTTP " + response.code();
    }

    public static boolean isAuthError(Response<?> response) {
        return response.code() == 401 || response.code() == 403;
    }
}