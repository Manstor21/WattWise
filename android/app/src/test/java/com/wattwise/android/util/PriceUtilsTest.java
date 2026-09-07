package com.wattwise.android.util;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * JVM-only tests for {@link PriceUtils}. The timezone conversion is the highest
 * risk surface: the backend serialises UTC timestamps WITHOUT a zone suffix;
 * verifying that a known UTC instant maps to the expected Spanish wall time
 * gives a solid safety net.
 */
public class PriceUtilsTest {

    /**
     * 2025-01-05 23:00 UTC = 2025-01-06 00:00 CET (winter time, UTC+1).
     */
    @Test
    public void utcToSpain_winterTime() {
        LocalDateTime utc = LocalDateTime.of(2025, 1, 5, 23, 0, 0);
        LocalDateTime spain = PriceUtils.utcToSpain(utc);
        assertEquals(LocalDateTime.of(2025, 1, 6, 0, 0, 0), spain);
    }

    /**
     * 2025-07-15 22:30 UTC = 2025-07-16 00:30 CEST (summer time, UTC+2).
     */
    @Test
    public void utcToSpain_summerTime() {
        LocalDateTime utc = LocalDateTime.of(2025, 7, 15, 22, 30, 0);
        LocalDateTime spain = PriceUtils.utcToSpain(utc);
        assertEquals(LocalDateTime.of(2025, 7, 16, 0, 30, 0), spain);
    }

    /**
     * A slot stored at 2025-01-05T08:15:00 UTC must belong to Spanish date
     * 2025-01-05 (09:15 CET). This is the grouping key used by the HOY / MAÑANA
     * tabs.
     */
    @Test
    public void spanishDateOfSlot_matchesLocalDate() {
        LocalDateTime utc = LocalDateTime.of(2025, 1, 5, 8, 15, 0);
        assertEquals(LocalDate.of(2025, 1, 5), PriceUtils.spanishDateOfSlot(utc));
    }

    @Test
    public void formatPrice_writesFourDecimalPlaces() {
        String formatted = PriceUtils.formatPrice(new BigDecimal("0.123456"));
        assertTrue(formatted.contains("0.1235"));
    }

    @Test
    public void formatPrice_null_returnsPlaceholder() {
        assertEquals("--", PriceUtils.formatPrice(null));
    }

    @Test
    public void windowLabel_showsSpanishTimes() {
        // Madrid 00:00 CET in winter = UTC 23:00 the previous day.
        LocalDateTime startUtc = LocalDateTime.of(2025, 1, 5, 23, 0, 0);
        LocalDateTime endUtc   = LocalDateTime.of(2025, 1, 6, 1, 0, 0); // Madrid 02:00
        String label = PriceUtils.windowLabel(startUtc, endUtc);
        assertEquals("00:00 – 02:00", label);
    }

    @Test
    public void spaniardDayKey_sortableLexicographically() {
        String key = PriceUtils.spanishDayKey(LocalDateTime.of(2025, 1, 5, 23, 0));
        assertEquals("2025-01-06", key);
    }
}