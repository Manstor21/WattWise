package com.wattwise.android.data.remote;

import com.wattwise.android.data.remote.dto.AlertPreferenceDto;
import com.wattwise.android.data.remote.dto.ApplianceDto;
import com.wattwise.android.data.remote.dto.AuthResponse;
import com.wattwise.android.data.remote.dto.LoginRequest;
import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.data.remote.dto.RecommendationDto;
import com.wattwise.android.data.remote.dto.RegisterRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Interfaz Retrofit que coincide exactamente con el contrato del backend
 * Spring Boot.
 *
 * <p>Los precios son públicos — no se necesita JWT. El {@link AuthInterceptor}
 * adjunta un token cuando existe, lo que es inofensivo para endpoints públicos.
 */
public interface ApiService {

    // ---- Auth (público) ----

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    // ---- Precios (público) ----

    @GET("api/prices/today")
    Call<List<PriceDto>> getTodayPrices();

    @GET("api/prices/tomorrow")
    Call<List<PriceDto>> getTomorrowPrices();

    /**
     * @param from YYYY-MM-DD
     * @param to   YYYY-MM-DD (incluido)
     */
    @GET("api/prices/range")
    Call<List<PriceDto>> getPricesRange(
            @Query("from") String from,
            @Query("to") String to);

    // ---- Electrodomésticos (JWT) ----

    @GET("api/appliances")
    Call<List<ApplianceDto>> getAppliances();

    @GET("api/appliances/{id}")
    Call<ApplianceDto> getAppliance(@Path("id") long id);

    @POST("api/appliances")
    Call<ApplianceDto> createAppliance(@Body ApplianceDto dto);

    @PUT("api/appliances/{id}")
    Call<ApplianceDto> updateAppliance(@Path("id") long id, @Body ApplianceDto dto);

    @DELETE("api/appliances/{id}")
    Call<Void> deleteAppliance(@Path("id") long id);

    // ---- Recomendaciones (JWT) ----

    @GET("api/recommendations")
    Call<List<RecommendationDto>> getRecommendations();

    @GET("api/recommendations")
    Call<List<RecommendationDto>> getRecommendationForAppliance(@Query("applianceId") long applianceId);

    // ---- Alertas (JWT) ----

    @GET("api/alerts/preferences")
    Call<AlertPreferenceDto> getAlertPreferences();

    @PUT("api/alerts/preferences")
    Call<AlertPreferenceDto> updateAlertPreferences(@Body AlertPreferenceDto dto);

    @GET("api/alerts/check")
    Call<List<String>> checkAlerts();
}