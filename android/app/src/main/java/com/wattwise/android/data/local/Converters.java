package com.wattwise.android.data.local;

import androidx.room.TypeConverter;

import java.math.BigDecimal;

/**
 * Conversores de tipo de Room. SQLite no tiene BigDecimal; los precios se
 * almacenan como texto para conservar precisión total (los precios son los
 * datos centrales de la app; nunca redondearlos).
 */
public class Converters {

    @TypeConverter
    public static BigDecimal fromString(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @TypeConverter
    public static String toString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}