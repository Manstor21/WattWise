package com.wattwise.android.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Local cache of a single 15-minute price slot. The primary key is the UTC
 * ISO timestamp, so re-inserting with {@code REPLACE} overwrites the slot —
 * prices are immutable per slot, making idempotent sync safe.
 *
 * <p>The server id is also kept so MANUAL/source metadata round-trips, but it is
 * not unique (two source systems could theoretically share ids across days).
 */
@Entity(tableName = "price_records", indices = {@Index(value = {"timestamp"}, unique = true)})
public class PriceRecordEntity {

    @PrimaryKey
    @NonNull
    public String timestamp; // UTC ISO-8601, e.g. "2025-01-05T23:00:00"

    public long serverId;
    public BigDecimal priceEurPerKwh;
    public BigDecimal plusTaxEurPerKwh;
    public BigDecimal totalEurPerKwh;
    public String source; // ESIOS | MANUAL
    public String color; // GREEN | AMBER | RED
}