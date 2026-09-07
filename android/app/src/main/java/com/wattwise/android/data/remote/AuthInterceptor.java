package com.wattwise.android.data.remote;

import android.text.TextUtils;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp interceptor that injects the {@code Authorization: Bearer} header
 * into every outgoing request when a JWT is present in the session. Prices
 * are public and need no token, but attaching it when absent does no harm.
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