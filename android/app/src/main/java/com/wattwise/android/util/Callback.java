package com.wattwise.android.util;

/**
 * Simple result channel used across repositories. {@code authError} is true
 * when the backing call failed with 401/403 — the UI then clears the session
 * and redirects to the login screen.
 *
 * <p>{@link #onError} is {@code default} so the interface stays functional
 * (lambdas can implement {@link #onSuccess} alone for local-only operations);
 * callers that care about failures override it.
 */
public interface Callback<T> {

    void onSuccess(T data);

    default void onError(boolean authError, String message) {
    }
}