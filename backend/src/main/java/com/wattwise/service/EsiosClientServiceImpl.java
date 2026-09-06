package com.wattwise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wattwise.exception.ExternalApiException;
import com.wattwise.model.dto.EsiosPricePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real implementation of {@link EsiosClientService} using RestTemplate.
 *
 * <p>Parses the ESIOS response shape:
 * <pre>
 * {
 *   "data": {
 *     "included": [
 *       { "attributes": { "title": "...", "values": [ {"value": 45.3, "datetime": "..."} ... ] } }
 *     ]
 *   }
 * }
 * </pre>
 *
 * <p>When several {@code included} components are present, the component with the
 * most non-null values is selected (the least noisy one). Values are EUR/MWh and
 * are kept raw here; conversion to EUR/kWh happens in {@link PriceService}.
 */
@Service
public class EsiosClientServiceImpl implements EsiosClientService {

    private static final Logger log = LoggerFactory.getLogger(EsiosClientServiceImpl.class);
    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiToken;

    public EsiosClientServiceImpl(@Value("${wattwise.esios.base-url}") String baseUrl,
                                  @Value("${wattwise.esios.api-token:}") String apiToken,
                                  @Value("${wattwise.esios.connect-timeout-ms:10000}") int connectTimeoutMs,
                                  @Value("${wattwise.esios.read-timeout-ms:30000}") int readTimeoutMs,
                                  ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EsiosPricePoint> fetchPricesForDate(LocalDate date) {
        String start = REQUEST_DATE.format(date.atStartOfDay());
        String end = REQUEST_DATE.format(date.plusDays(1).atStartOfDay());
        String url = baseUrl + "?start_date=" + start + "&end_date=" + end + "&time_trunc=15";

        HttpHeaders headers = new HttpHeaders();
        if (apiToken != null && !apiToken.isBlank()) {
            headers.set("Authorization", "Bearer " + apiToken);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ExternalApiException("ESIOS responded with status " + response.getStatusCode());
            }
            return parse(response.getBody(), date);
        } catch (RestClientException ex) {
            throw new ExternalApiException("ESIOS request failed for " + date + ": " + ex.getMessage(), ex);
        }
    }

    /** Robust parser: selects the most complete included component and merges by datetime. */
    List<EsiosPricePoint> parse(String json, LocalDate date) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode included = root.path("data").path("included");

            List<Component> components = new ArrayList<>();
            for (JsonNode node : included) {
                String title = node.path("attributes").path("title").asText("<unknown>");
                Map<LocalDateTime, BigDecimal> values = new LinkedHashMap<>();
                long nonNull = 0;
                JsonNode valueArray = node.path("attributes").path("values");
                for (JsonNode v : valueArray) {
                    if (!v.hasNonNull("value") || !v.hasNonNull("datetime")) {
                        continue;
                    }
                    // Filter on the offset datetime's LOCAL date (Spain wall-clock) so
                    // 00:00-07:00 morning slots are not dropped when normalized to UTC.
                    java.time.OffsetDateTime odt = parseDatetime(v.path("datetime").asText());
                    if (odt != null && odt.toLocalDate().equals(date)) {
                        LocalDateTime utc = odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                        values.put(utc, new BigDecimal(v.path("value").asText()));
                        nonNull++;
                    }
                }
                if (nonNull > 0) {
                    components.add(new Component(title, values, nonNull));
                }
            }

            if (components.isEmpty()) {
                throw new ExternalApiException("ESIOS returned no parsable price data for " + date);
            }

            // Prefer the component titled like the aggregate PVPC price; fallback to the
            // component with the most data points.
            Component best = components.stream()
                    .max(Comparator.comparingLong((Component c) -> partsScore(c.title))
                            .thenComparingLong(c -> c.nonNull))
                    .orElseThrow();

            List<EsiosPricePoint> points = best.values.entrySet().stream()
                    .map(e -> new EsiosPricePoint(e.getKey(), e.getValue()))
                    .sorted(Comparator.comparing(EsiosPricePoint::datetime))
                    .toList();

            log.debug("Parsed {} price points from ESIOS for {} (component '{}')",
                    points.size(), date, best.title);
            return points;
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalApiException("Failed to parse ESIOS response for " + date + ": " + ex.getMessage(), ex);
        }
    }

    /** Simple title scoring: prefer titles mentioning the PVPC aggregate. */
    private static int partsScore(String title) {
        String t = title.toLowerCase();
        int score = 0;
        if (t.contains("pvpc")) score += 2;
        if (t.contains("peajes") || t.contains("mercado")) score += 1;
        if (t.contains("total")) score += 1;
        return score;
    }

    private java.time.OffsetDateTime parseDatetime(String raw) {
        try {
            return java.time.OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            log.warn("Skipping unparseable ESIOS datetime '{}'", raw);
            return null;
        }
    }

    private record Component(String title, Map<LocalDateTime, BigDecimal> values, long nonNull) {
    }
}