package com.wattwise.android.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

/**
 * Caché local de una franja de precio de 15 minutos. La clave primaria es la
 * marca de tiempo UTC en ISO, por lo que reinsertar con {@code REPLACE} sobrescribe
 * la franja — los precios son inmutables por franja, lo que hace seguro el
 * sync idempotente.
 *
 * <p>También se conserva el id del servidor para que la metadatos MANUAL/fuente
 * hagan round-trip, pero no es único (dos sistemas de origen podrían teóricamente
 * compartir ids entre días).
 */
@Entity(tableName = "price_records", indices = {@Index(value = {"timestamp"}, unique = true)})
public class PriceRecordEntity {

    @PrimaryKey
    @NonNull
    public String timestamp; // UTC ISO-8601, p. ej. "2025-01-05T23:00:00"

    public long serverId;
    public BigDecimal priceEurPerKwh;
    public BigDecimal plusTaxEurPerKwh;
    public BigDecimal totalEurPerKwh;
    public String source; // Origen: ESIOS | MANUAL
    public String color; // Color: GREEN | AMBER | RED
}