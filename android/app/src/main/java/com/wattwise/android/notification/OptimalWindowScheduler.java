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
 * Encuentra la ventana contigua más barata de slots de 15 minutos dentro de una
 * lista de precios y deriva el payload de la notificación (límites de la ventana,
 * precio representativo, color del semáforo).
 *
 * <p>Java puro (solo {@link PriceDto} y java.time) para poder probarse en la JVM.
 * Se asume que los slots están ordenados por {@code timestamp}; el llamador es
 * responsable de filtrar los slots futuros y de mantenerlos en orden ascendente.
 */
public final class OptimalWindowScheduler {

    /** Resultado de la búsqueda. Las horas son naive-UTC (semántica del backend). */
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

    private static final int SLOTS_PER_HOUR = 4; // slots PVPC de 15 minutos

    private OptimalWindowScheduler() {
    }

    /**
     * Encuentra la ventana de slots más barata y contigua de longitud {@code windowHours}.
     *
     * @param slots       slots de precio ordenados (timestamps UTC). Pueden estar desordenados; ordenamos una copia.
     * @param windowHours longitud de ventana contigua solicitada; < 0.25 se limita a 1 slot.
     * @return la ventana más barata o {@code null} si hay demasiado pocos slots.
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

        // Suma de ventana deslizante sobre totalEurPerKwh (null = tratado como 0 y omitido abajo).
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
     * Semáforo representativo de una ventana: el color del slot más barato en ella
     * (el motor de la decisión de planificación), con retroceso al siguiente color.
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

    /** Precio de un único slot más barato + su etiqueta de hora de Madrid, para alertas de umbral. */
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

    /** True cuando al menos un slot es más barato que {@code threshold}. */
    public static boolean hasPriceBelow(List<PriceDto> slots, BigDecimal threshold) {
        if (threshold == null) {
            return false;
        }
        PriceDto lowest = lowestPrice(slots);
        return lowest != null && priceOf(lowest).compareTo(threshold) < 0;
    }

    /** Media de los precios totales (media naive, para contextualizar el umbral). */
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

    /** Solo slots cuya hora local de España es posterior a {@code nowUtc}. */
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