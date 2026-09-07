package com.wattwise.android.data.remote;

import com.wattwise.android.BuildConfig;
import com.wattwise.android.util.GsonProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton Retrofit client. {@link #init} is called from
 * {@link com.wattwise.android.WattWiseApp} with a fresh
 * {@link SessionManager} so the token is available before the first HTTP
 * request the UI fires.
 */
public final class ApiClient {

    private static final long TIMEOUT = 30L; // seconds

    private static volatile ApiService apiService;

    private ApiClient() {
    }

    public static void init(SessionManager session) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient ok = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(session))
                .addInterceptor(log)
                .build();

        // Backend expects a trailing slash for Retrofit to merge relative paths correctly.
        String baseUrl = BuildConfig.API_BASE_URL;
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        apiService = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(ok)
                .addConverterFactory(GsonConverterFactory.create(GsonProvider.get()))
                .build()
                .create(ApiService.class);
    }

    public static ApiService api() {
        if (apiService == null) {
            throw new IllegalStateException("ApiClient.init(session) must be called first");
        }
        return apiService;
    }
}