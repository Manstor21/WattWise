package com.wattwise.android.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Recomendación del servidor almacenada en caché. Una fila por electrodoméstico
 * (la API devuelve como máximo una recomendación por electrodoméstico), claveada
 * por el id del electrodoméstico en el servidor. La lista de la ventana
 * {@link com.wattwise.android.data.remote.dto.PriceDto} se almacena como JSON
 * (ver {@link com.wattwise.android.util.GsonProvider}).
 */
@Entity(tableName = "recommendations")
public class RecommendationEntity {

    @PrimaryKey
    public long applianceId;

    public String applianceName;
    public String applianceType;
    public String recommendedStart; // UTC ISO-8601
    public String recommendedEnd;   // UTC ISO-8601
    public BigDecimal recommendedPriceEurPerKwh;
    public BigDecimal estimatedCostEur;
    public BigDecimal worstCaseCostEur;
    public BigDecimal estimatedSavingsEur;
    public BigDecimal savingsPercentage;
    public String semaphore; // Semáforo: GREEN | AMBER | RED
    public String windowJson;

    /** Milisegundos epoch de la descarga que produjo esta fila. */
    public long lastUpdated;
}