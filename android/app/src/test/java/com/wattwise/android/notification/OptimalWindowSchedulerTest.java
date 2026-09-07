package com.wattwise.android.notification;

import com.wattwise.android.data.remote.dto.PriceDto;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Pure-JVM tests of {@link OptimalWindowScheduler}: the cheapest 2-hour (8-slot)
 * window detection, mean price computation and the single-slot low-price helper.
 * No Android runtime required.
 */
public class OptimalWindowSchedulerTest {

    @Test
    public void nullInput_returnsNull() {
        assertNull(OptimalWindowScheduler.findCheapestWindow(null, 2));
    }

    @Test
    public void emptyInput_returnsNull() {
        assertNull(OptimalWindowScheduler.findCheapestWindow(new ArrayList<>(), 2));
    }

    @Test
    public void fewerSlotsThanWindow_returnsNull() {
        // 3 slots, but the window needs 8 (2h ÷ 15min).
        List<PriceDto> three = Arrays.asList(
                slot("2025-01-01T10:00:00", "0.1200", "GREEN"),
                slot("2025-01-01T10:15:00", "0.1100", "GREEN"),
                slot("2025-01-01T10:30:00", "0.1300", "GREEN"));
        assertNull(OptimalWindowScheduler.findCheapestWindow(three, 2));
    }

    @Test
    public void picksLowestAverageWindow() {
        // 12 slots. Slots 4..11 contain the cheapest 8-slot window.
        List<PriceDto> slots = Arrays.asList(
                slot("2025-01-01T10:00:00", "0.20", "RED"),
                slot("2025-01-01T10:15:00", "0.22", "RED"),
                slot("2025-01-01T10:30:00", "0.21", "RED"),
                slot("2025-01-01T10:45:00", "0.23", "RED"),
                slot("2025-01-01T11:00:00", "0.08", "GREEN"),
                slot("2025-01-01T11:15:00", "0.07", "GREEN"),
                slot("2025-01-01T11:30:00", "0.09", "GREEN"),
                slot("2025-01-01T11:45:00", "0.08", "GREEN"),
                slot("2025-01-01T12:00:00", "0.06", "GREEN"),
                slot("2025-01-01T12:15:00", "0.07", "GREEN"),
                slot("2025-01-01T12:30:00", "0.08", "GREEN"),
                slot("2025-01-01T12:45:00", "0.09", "GREEN"));

        OptimalWindowScheduler.Window w =
                OptimalWindowScheduler.findCheapestWindow(slots, 2);

        assertNotNull(w);
        assertEquals(LocalDateTime.of(2025, 1, 1, 11, 0), w.startUtc);
        assertEquals(LocalDateTime.of(2025, 1, 1, 13, 0), w.endUtc);
        assertEquals(8, w.slotCount);
        // The cheapest 8-slot average from 11:00 is 0.62 ÷ 8 = 0.0775.
        assertNotNull(w.averageEurPerKwh);
        assertEquals(0.0775, w.averageEurPerKwh.doubleValue(), 1e-4);
        assertEquals("GREEN", w.semaphore);
    }

    @Test
    public void meanPrice_calculatesCorrectly() {
        List<PriceDto> prices = Arrays.asList(
                slot("2025-01-01T10:00:00", "0.10", "GREEN"),
                slot("2025-01-01T10:15:00", "0.20", "AMBER"));
        BigDecimal mean = OptimalWindowScheduler.meanPrice(prices);
        assertEquals(0.15, mean.doubleValue(), 1e-6);
    }

    @Test
    public void lowestPrice_returnsMinSlot() {
        List<PriceDto> prices = Arrays.asList(
                slot("2025-01-01T10:00:00", "0.25", "RED"),
                slot("2025-01-01T10:15:00", "0.05", "GREEN"),
                slot("2025-01-01T10:30:00", "0.15", "AMBER"));
        PriceDto low = OptimalWindowScheduler.lowestPrice(prices);
        assertNotNull(low);
        assertEquals(LocalDateTime.of(2025, 1, 1, 10, 15), low.getTimestamp());
    }

    private static PriceDto slot(String ts, String total, String color) {
        PriceDto p = new PriceDto();
        p.setTimestamp(LocalDateTime.parse(ts));
        p.setTotalEurPerKwh(new BigDecimal(total));
        p.setColor(color);
        return p;
    }
}