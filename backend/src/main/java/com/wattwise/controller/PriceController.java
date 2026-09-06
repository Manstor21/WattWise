package com.wattwise.controller;

import com.wattwise.model.dto.PriceDto;
import com.wattwise.service.PriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Price queries. Responds with classified (traffic-light) slots, always UTC
 * timestamps. Read access is public (see SecurityConfig rationale); writes of
 * price data are ADMIN-only.
 */
@RestController
@RequestMapping("/api/prices")
@Tag(name = "Prices", description = "PVPC electricity prices with traffic-light classification")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("/today")
    @Operation(summary = "Today's price slots (96 x 15min) with traffic-light color")
    public List<PriceDto> today() {
        return priceService.getToday();
    }

    @GetMapping("/tomorrow")
    @Operation(summary = "Tomorrow's price slots, if published")
    public List<PriceDto> tomorrow() {
        return priceService.getTomorrow();
    }

    @GetMapping("/range")
    @Operation(summary = "Historical prices within an inclusive [from, to] date range (Spanish dates)")
    public List<PriceDto> range(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return priceService.getRange(from, to);
    }
}