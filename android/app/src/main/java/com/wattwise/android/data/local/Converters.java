package com.wattwise.android.data.local;

import androidx.room.TypeConverter;

import java.math.BigDecimal;

/**
 * Room type converters. SQLite has no BigDecimal; prices are stored as text so
 * we keep full precision (prices are the app's core data, never round them).
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