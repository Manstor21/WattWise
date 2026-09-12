package com.wattwise.service;

import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del {@link BacktestCsvLoader}: carga del dataset de ejemplo del classpath
 * y control de errores ante CSVs malformados (cabecera, fila y timestamp).
 */
class BacktestCsvLoaderTest {

    @Test
    void loadAllParsesTheSampleDatasetDeterministically() {
        List<PriceRecord> records = new BacktestCsvLoader().loadAll();

        // 12 meses completos: 365 días x 96 slots de 15 minutos.
        assertThat(records).hasSize(35040);
        assertThat(records.get(0).getTimestamp()).isEqualTo(LocalDateTime.of(2025, 9, 1, 0, 0));
        assertThat(records.get(records.size() - 1).getTimestamp())
                .isEqualTo(LocalDateTime.of(2026, 8, 31, 23, 45));
    }

    @Test
    void loadAllConvertsEurPerMwhToEurPerKwhAndKeepsSourceEsiOs() {
        List<PriceRecord> records = new BacktestCsvLoader().loadAll();

        // Primera fila del fichero: 2025-09-01T00:00:00Z,47.7740 -> 0.047774 EUR/kWh.
        PriceRecord first = records.get(0);
        assertThat(first.getTotalEurPerKwh()).isEqualByComparingTo("0.047774");
        assertThat(first.getPriceEurPerKwh()).isEqualByComparingTo(first.getTotalEurPerKwh());
        assertThat(first.getPlusTaxEurPerKwh()).isNull();
        assertThat(first.getSource()).isEqualTo(PriceSource.ESIOS);
    }

    @Test
    void loadAllIsIdempotent() {
        BacktestCsvLoader loader = new BacktestCsvLoader();
        assertThat(loader.loadAll()).hasSameSizeAs(loader.loadAll());
        assertThat(loader.loadAll().get(10).getTimestamp())
                .isEqualTo(loader.loadAll().get(10).getTimestamp());
    }

    @Test
    void malformedHeaderIsRejected() {
        BacktestCsvLoader loader = new BacktestCsvLoader("backtest/malformed-header.csv");

        assertThatThrownBy(loader::loadAll)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cabecera inesperada");
    }

    @Test
    void malformedPriceRowIsRejected() {
        BacktestCsvLoader loader = new BacktestCsvLoader("backtest/malformed-row.csv");

        assertThatThrownBy(loader::loadAll)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precio no numérico")
                .hasMessageContaining("Línea 3");
    }

    @Test
    void malformedTimestampIsRejected() {
        BacktestCsvLoader loader = new BacktestCsvLoader("backtest/invalid-timestamp.csv");

        assertThatThrownBy(loader::loadAll)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp no parseable");
    }
}