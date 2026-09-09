package com.wattwise.android.util;

/**
 * Canal de resultados sencillo usado en los repositorios. {@code authError} es
 * true cuando la llamada subyacente falló con 401/403: la UI limpia entonces la
 * sesión y redirige a la pantalla de inicio de sesión.
 *
 * <p>{@link #onError} es {@code default} para que la interfaz siga siendo
 * funcional (los lambdas pueden implementar solo {@link #onSuccess} para
 * operaciones locales); quien necesite controlar los fallos lo sobrescribe.
 */
public interface Callback<T> {

    void onSuccess(T data);

    default void onError(boolean authError, String message) {
    }
}