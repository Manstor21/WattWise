package com.wattwise.android.data.remote;

import com.wattwise.android.BuildConfig;
import com.wattwise.android.util.GsonProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente Retrofit singleton. {@link #init} se invoca desde
 * {@link com.wattwise.android.WattWiseApp} con un {@link SessionManager}
 * recién creado para que el token esté disponible antes de la primera
 * petición HTTP que lance la UI.
 */
public final class ApiClient {

    private static final long TIMEOUT = 30L; // segundos

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

        // El backend espera una barra final para que Retrofit combine las rutas relativas correctamente.
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