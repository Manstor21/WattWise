package com.wattwise.android.notification;

import com.wattwise.android.data.remote.dto.PriceDto;
import com.wattwise.android.util.PriceUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the cheapest contiguous window of 15-minute slots inside a price list
 * and derives the notification payload (window bounds, representative price,
 * semaphore colour).
 *
 * <p>Pure Java (only {@link PriceDto} and java.time) so it can be JVM-tested.
 * Slots are assumed sorted by {@code timestamp}; the caller is responsible for
 * filtering to the future slots and for keeping them in ascending order.
 */
public final class OptimalWindowScheduler {

    /** Result of the search. Times are naive-UTC (backend semantics). */
    public static final class Window {
        public final LocalDateTime startUtc;
        public final LocalDateTime endUtc;
        public final BigDecimal averageEurPerKwh;
        public final String semaphore;
        public final int slotCount;

        Window(LocalDateTime startUtc, LocalDateTime endUtc, BigDecimal averageEurPerKwh,
               String semaphore, int slotCount) {
            this.startUtc = startUtc;
            this.endUtc = endUtc;
            this.averageEurPerKwh = averageEurPerKwh;
            this.semaphore = semaphore;
            this.slotCount = slotCount;
        }
    }

    private static final int SLOTS_PER_HOUR = 4; // 15-minute PVPC slots

    private OptimalWindowScheduler() {
    }

    /**
     * Finds the {@code windowHours}-long cheapest slot window.
     *
     * @param slots       sorted price slots (UTC timestamps). May be unsorted; we sort a copy.
     * @param windowHours requested contiguous window length; < 0.25 clamps to 1 slot.
     * @return the cheapest window or {@code null} if too few slots.
     */
    public static Window findCheapestWindow(List<PriceDto> slots, double windowHours) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        List<PriceDto> sorted = new ArrayList<>(slots);
        sorted.sort(Comparator.comparing(PriceDto::getTimestamp));

        int windowSlots = Math.max(1, (int) Math.ceil(windowHours * SLOTS_PER_HOUR));
        if (sorted.size() < windowSlots) {
            return null;
        }

        // Sliding-window sum over totalEurPerKwh (null = treated as 0 and skipped below).
        double sum = 0;
        int counted = 0;
        double bestAvg = Double.POSITIVE_INFINITY;
        int bestStart = -1;
        String bestSemaphore = null;

        for (int i = 0; i < sorted.size(); i++) {
            BigDecimal p = priceOf(sorted.get(i));
            if (p != null) {
                sum += p.doubleValue();
                counted++;
            }
            if (i >= windowSlots) {
                BigDecimal removed = priceOf(sorted.get(i - windowSlots));
                if (removed != null) {
                    sum -= removed.doubleValue();
                    counted--;
                }
            }
            if (i >= windowSlots - 1 && counted > 0) {
                double avg = sum / counted;
                if (avg < bestAvg) {
                    bestAvg = avg;
                    bestStart = i - windowSlots + 1;
                    bestSemaphore = windowSemaphore(sorted.subList(bestStart, bestStart + windowSlots));
                }
            }
        }

        if (bestStart < 0) {
            return null;
        }

        List<PriceDto> best = sorted.subList(bestStart, bestStart + windowSlots);
        LocalDateTime start = best.get(0).getTimestamp();
        LocalDateTime end = best.get(best.size() - 1).getTimestamp().plusMinutes(15);
        BigDecimal average = BigDecimal.valueOf(bestAvg).setScale(5, java.math.RoundingMode.HALF_UP);
        return new Window(start, end, average, bestSemaphore, best.size());
    }

    /**
     * Representative semaphore for a window: the colour of the cheapest slot in it
     * (the driver of the scheduling decision), falling back to the next colour.
     */
    public static String windowSemaphore(List<PriceDto> window) {
        if (window == null || window.isEmpty()) {
            return "AMBER";
        }
        List<PriceDto> copy = new ArrayList<>(window);
        copy.sort(Comparator.comparing(PriceDto::getTimestamp));
        PriceDto cheapest = null;
        for (PriceDto p : copy) {
            if (priceOf(p) == null) {
                continue;
            }
            if (cheapest == null || priceOf(p).compareTo(priceOf(cheapest)) < 0) {
                cheapest = p;
            }
        }
        return cheapest != null && cheapest.getColor() != null ? cheapest.getColor() : "AMBER";
    }

    /** Cheapest single-slot price + its Madrid time label, for threshold alerts. */
    public static PriceDto lowestPrice(List<PriceDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        PriceDto lowest = null;
        for (PriceDto p : slots) {
            if (priceOf(p) == null) {
                continue;
            }
            if (lowest == null || priceOf(p).compareTo(priceOf(lowest)) < 0) {
                lowest = p;
            }
        }
        return lowest;
    }

    /** True when at least one slot is cheaper than {@code threshold}. */
    public static boolean hasPriceBelow(List<PriceDto> slots, BigDecimal threshold) {
        if (threshold == null) {
            return false;
        }
        PriceDto lowest = lowestPrice(slots);
        return lowest != null && priceOf(lowest).compareTo(threshold) < 0;
    }

    /** Mean of the total prices (naive average, for threshold framing). */
    public static BigDecimal meanPrice(List<PriceDto> slots) {
        if (slots == null || slots.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (PriceDto p : slots) {
            BigDecimal v = priceOf(p);
            if (v != null) {
                sum = sum.add(v);
                n++;
            }
        }
        return n == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(n), 6, java.math.RoundingMode.HALF_UP);
    }

    /** Only slots whose Spanish wall time is after {@code nowUtc}. */
    public static List<PriceDto> futureSlots(List<PriceDto> slots, LocalDateTime nowUtc) {
        if (slots == null || slots.isEmpty()) {
            return Collections.emptyList();
        }
        List<PriceDto> future = new ArrayList<>();
        for (PriceDto p : slots) {
            if (p.getTimestamp() != null && (p.getTimestamp().isAfter(nowUtc)
                    || PriceUtils.spanishDateOfSlot(p.getTimestamp()).isAfter(nowUtc.toLocalDate()))) {
                future.add(p);
            }
        }
        future.sort(Comparator.comparing(PriceDto::getTimestamp));
        return future;
    }

    private static BigDecimal priceOf(PriceDto p) {
        return p == null ? null : p.getTotalEurPerKwh();
    }
}