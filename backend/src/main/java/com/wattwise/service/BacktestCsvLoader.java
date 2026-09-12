package com.wattwise.service;

import com.wattwise.model.entity.PriceRecord;
import com.wattwise.model.enums.PriceSource;
import com.wattwise.util.PriceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga el dataset histórico de precios desde un CSV del classpath con formato
 * ESIOS y lo convierte a {@link PriceRecord} en EUR/kWh.
 *
 * <p>Formato de cabecera: {@code timestamp,price_eur_per_mwh}. El {@code timestamp}
 * es ISO-8601 en UTC y el precio bruto en EUR/MWh (como publica ESIOS); el loader lo
 * convierte a EUR/kWh con {@link PriceUtils#eurPerMwhToEurPerKwh}, de modo que los
 * registros resultantes son equivalentes a los que persistiría la ingesta diaria.
 *
 * <p>Idempotente y sin estado: cada llamada re-parsea el recurso del classpath.
 */
@Service
public class BacktestCsvLoader {

    private static final Logger log = LoggerFactory.getLogger(BacktestCsvLoader.class);

    /** Ruta del dataset de ejemplo por defecto en el classpath. */
    public static final String DEFAULT_RESOURCE_PATH = "backtest/pvpc-history-sample.csv";

    private static final String EXPECTED_HEADER = "timestamp,price_eur_per_mwh";

    private final String resourcePath;

    public BacktestCsvLoader() {
        this(DEFAULT_RESOURCE_PATH);
    }

    /**
     * Permite apuntar a un CSV distinto en el classpath (p. ej. fixtures de test).
     *
     * @param resourcePath ruta del recurso CSV
     */
    public BacktestCsvLoader(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * Lee y convierte todos los registros del CSV.
     *
     * @return registros de precio en el orden del fichero
     * @throws IllegalArgumentException si el recurso no existe, la cabecera es
     *                                  inválida o alguna línea es malformada
     */
    public List<PriceRecord> loadAll() {
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("El CSV de precios está vacío: " + resourcePath);
            }
            if (!EXPECTED_HEADER.equals(header.trim())) {
                throw new IllegalArgumentException(
                        "Cabecera inesperada en " + resourcePath + ": '" + header.trim() + "' (se esperaba: "
                                + EXPECTED_HEADER + ")");
            }
            List<PriceRecord> records = new ArrayList<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                records.add(parseLine(line, lineNumber));
            }
            if (records.isEmpty()) {
                throw new IllegalArgumentException("El CSV de precios no contiene ningún registro: " + resourcePath);
            }
            log.debug("Cargados {} registros de precio desde {}", records.size(), resourcePath);
            return records;
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el CSV de precios del classpath: " + resourcePath, ex);
        }
    }

    private PriceRecord parseLine(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 2) {
            throw malformed(lineNumber, "se esperaban 2 columnas (timestamp,price_eur_per_mwh)");
        }
        String timestampText = fields[0].trim();
        String priceText = fields[1].trim();

        LocalDateTime timestamp;
        try {
            timestamp = PriceUtils.parseIsoUtc(timestampText);
        } catch (RuntimeException ex) {
            throw malformed(lineNumber, "timestamp no parseable: '" + timestampText + "'");
        }

        BigDecimal pricePerMwh;
        try {
            pricePerMwh = new BigDecimal(priceText);
        } catch (NumberFormatException ex) {
            throw malformed(lineNumber, "precio no numérico: '" + priceText + "'");
        }

        // Los precios negativos no tienen sentido en un histórico realista de
        // promedios; se normalizan a cero como salvaguarda de los agregados.
        if (pricePerMwh.signum() < 0) {
            pricePerMwh = BigDecimal.ZERO;
        }

        BigDecimal pricePerKwh = PriceUtils.eurPerMwhToEurPerKwh(pricePerMwh);
        PriceRecord record = new PriceRecord();
        record.setTimestamp(timestamp);
        record.setPriceEurPerKwh(pricePerKwh);
        record.setPlusTaxEurPerKwh(null);
        record.setTotalEurPerKwh(pricePerKwh);
        record.setSource(PriceSource.ESIOS);
        return record;
    }

    private IllegalArgumentException malformed(int lineNumber, String detail) {
        return new IllegalArgumentException(
                "Línea " + lineNumber + " malformada en " + resourcePath + ": " + detail);
    }
}