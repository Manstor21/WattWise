package com.wattwise.android.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Cached server recommendation. One row per appliance (the API returns at most
 * one recommendation per appliance), keyed by the appliance server id.
 * The window's {@link com.wattwise.android.data.remote.dto.PriceDto}
 * list is stored as JSON (see {@link com.wattwise.android.util.GsonProvider}).
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
    public String semaphore; // GREEN | AMBER | RED
    public String windowJson;

    /** Epoch millis of the fetch that produced this row. */
    public long lastUpdated;
}