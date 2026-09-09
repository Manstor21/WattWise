package com.wattwise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wattwise.exception.ExternalApiException;
import com.wattwise.model.dto.EsiosPricePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsiosClientServiceImplTest {

    private EsiosClientServiceImpl client;

    @BeforeEach
    void setUp() {
        client = new EsiosClientServiceImpl(
                "http://localhost:9999/datos/mercados/precios-mercados-tiempo-real",
                "", 100, 100, new ObjectMapper());
    }

    @Test
    void parsesBestComponentAndSkipsNullValues() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        String json = """
                {
                  "data": {
                    "included": [
                      {
                        "attributes": {
                          "title": "Demanda",
                          "values": []
                        }
                      },
                      {
                        "attributes": {
                          "title": "Precio mercado PVPC",
                          "values": [
                            {"value": 45.32, "datetime": "2025-01-05T00:00:00.000+01:00"},
                            {"value": null, "datetime": "2025-01-05T00:15:00.000+01:00"},
                            {"value": 50.00, "datetime": "2025-01-05T00:30:00.000+01:00"}
                          ]
                        }
                      }
                    ]
                  }
                }
                """;

        List<EsiosPricePoint> points = client.parse(json, date);

        assertThat(points).hasSize(2);
        // Offset +01:00 normalizado a UTC.
        assertThat(points.get(0).datetime()).isEqualTo(LocalDateTime.of(2025, 1, 4, 23, 0));
        assertThat(points.get(0).valueEurPerMwh()).isEqualByComparingTo(new BigDecimal("45.32"));
        // 2025-01-05T00:30+01:00 == 2025-01-04T23:30Z.
        assertThat(points.get(1).datetime()).isEqualTo(LocalDateTime.of(2025, 1, 4, 23, 30));
    }

    @Test
    void filtersOutValuesFromOtherDates() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        String json = """
                {
                  "data": {
                    "included": [
                      {
                        "attributes": {
                          "title": "PVPC",
                          "values": [
                            {"value": 10.0, "datetime": "2025-01-05T00:00:00.000+01:00"},
                            {"value": 20.0, "datetime": "2025-01-06T00:00:00.000+01:00"}
                          ]
                        }
                      }
                    ]
                  }
                }
                """;

        List<EsiosPricePoint> points = client.parse(json, date);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).valueEurPerMwh()).isEqualByComparingTo("10.0");
    }

    @Test
    void throwsExternalApiExceptionOnEmptyData() {
        String json = """
                {
                  "data": {
                    "included": [ {"attributes": {"title": "PVPC", "values": []} } ]
                  }
                }
                """;

        assertThatThrownBy(() -> client.parse(json, LocalDate.of(2025, 1, 5)))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("no parsable price data");
    }

    @Test
    void throwsExternalApiExceptionOnGarbageJson() {
        assertThatThrownBy(() -> client.parse("this is not json", LocalDate.of(2025, 1, 5)))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void throwsExternalApiExceptionOnMissingDataNode() {
        assertThatThrownBy(() -> client.parse("{\"foo\":\"bar\"}", LocalDate.of(2025, 1, 5)))
                .isInstanceOf(ExternalApiException.class);
    }
}