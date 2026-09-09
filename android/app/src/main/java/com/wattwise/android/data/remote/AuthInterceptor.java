package com.wattwise.android.data.remote;

import android.text.TextUtils;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Interceptor OkHttp que inyecta la cabecera {@code Authorization: Bearer}
 * en cada petición saliente cuando hay un JWT en la sesión. Los precios son
 * públicos y no necesitan token, pero adjuntarlo cuando no existe no hace daño.
 */
public final class AuthInterceptor implements Interceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final SessionManager session;

    public AuthInterceptor(SessionManager session) {
        this.session = session;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = session.getToken();
        Request.Builder builder = chain.request().newBuilder();
        if (!TextUtils.isEmpty(token)) {
            builder.addHeader(HEADER, PREFIX + token);
        }
        return chain.proceed(builder.build());
    }
}