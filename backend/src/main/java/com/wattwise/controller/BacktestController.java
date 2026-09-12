package com.wattwise.controller;

import com.wattwise.model.dto.BacktestReportDto;
import com.wattwise.service.BacktestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backtesting sobre el histórico PVPC: simula el motor de semáforo y de
 * recomendaciones sobre un periodo de meses y cuantifica el ahorro de programar
 * los electrodomésticos en su ventana óptima. Requiere JWT (regla genérica
 * {@code /api/**} de {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/backtest")
@Tag(name = "Backtest", description = "Backtesting over historical PVPC prices (JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * Informe de backtesting del histórico (coste naïve frente a coste optimizado).
     *
     * @param months número de meses (más recientes) del dataset a analizar; por defecto 12
     * @return el informe agregado con desglose mensual
     */
    @GetMapping("/report")
    @Operation(summary = "Backtest report over N months of historical PVPC prices",
            description = "Simula el motor de semáforo y recomendaciones sobre el histórico y devuelve "
                    + "el ahorro agregado y por mes de programar los electrodomésticos en su ventana óptima.")
    public BacktestReportDto report(@RequestParam(value = "months", defaultValue = "12") int months) {
        return backtestService.generateReport(months);
    }
}