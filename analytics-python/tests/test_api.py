"""Tests de los endpoints de la API REST mediante el cliente de pruebas Flask."""

import json

import pytest


class TestHealthEndpoints:
    """Sondas de liveness y readiness."""

    def test_health(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"

    def test_ready_success(self, client):
        resp = client.get("/ready")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ready"


class TestWeekdayAveragesAPI:
    """/api/analytics/weekday-averages endpoint."""

    def test_200_with_data(self, client, synthetic_prices):
        resp = client.get("/api/analytics/weekday-averages?days=30")
        assert resp.status_code == 200
        data = resp.get_json()
        assert "days" in data
        assert "averages" in data
        assert len(data["averages"]) == 7

    def test_404_no_data(self, client):
        resp = client.get("/api/analytics/weekday-averages?days=30")
        assert resp.status_code == 404

    def test_400_bad_days(self, client):
        resp = client.get("/api/analytics/weekday-averages?days=abc")
        assert resp.status_code == 400
        data = resp.get_json()
        assert "error" in data

    def test_400_negative_days(self, client):
        resp = client.get("/api/analytics/weekday-averages?days=-5")
        assert resp.status_code == 400

    def test_default_days(self, client, synthetic_prices):
        resp = client.get("/api/analytics/weekday-averages")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["days"] == 30  # valor por defecto de la configuración


class TestSavingsEstimateAPI:
    """/api/analytics/savings-estimate endpoint."""

    def test_200_with_data(self, client, synthetic_prices):
        resp = client.get("/api/analytics/savings-estimate?userId=1")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["userId"] == 1
        assert "totalSavingsEur" in data

    def test_200_no_user_id(self, client, synthetic_prices):
        resp = client.get("/api/analytics/savings-estimate")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["userId"] is None

    def test_400_bad_user_id(self, client):
        resp = client.get("/api/analytics/savings-estimate?userId=abc")
        assert resp.status_code == 400


class TestTrendAPI:
    """/api/analytics/trend endpoint."""

    def test_200_with_data(self, client, synthetic_prices):
        resp = client.get("/api/analytics/trend?period=30d")
        assert resp.status_code == 200
        data = resp.get_json()
        assert "direction" in data
        assert data["direction"] in ("up", "down", "flat")
        assert "slope" in data
        assert "pctChange" in data

    def test_200_default_period(self, client, synthetic_prices):
        resp = client.get("/api/analytics/trend")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["period"] == "30d"

    def test_200_with_data_no_period(self, client, synthetic_prices):
        resp = client.get("/api/analytics/trend")
        assert resp.status_code == 200

    def test_400_bad_period(self, client):
        resp = client.get("/api/analytics/trend?period=abc")
        assert resp.status_code == 400


class TestAnomaliesAPI:
    """/api/analytics/anomalies endpoint."""

    def test_200_with_data(self, client, synthetic_prices):
        resp = client.get("/api/analytics/anomalies")
        assert resp.status_code == 200
        data = resp.get_json()
        assert "count" in data
        assert "thresholdZ" in data
        assert "anomalies" in data
        assert data["thresholdZ"] == 3.0

    def test_anomaly_detected(self, client, synthetic_prices):
        resp = client.get("/api/analytics/anomalies")
        data = resp.get_json()
        # Los datos sintéticos tienen un pico bruto de 0.50 €/kWh; el reporte de anomalías
        # usa total_eur_per_kwh (precio + 21% IVA) = 0.50 * 1.21 = 0.605.
        assert data["count"] >= 1
        assert any(
            a["priceEurPerKwh"] == pytest.approx(0.605, abs=1e-6)
            for a in data["anomalies"]
        )

    def test_custom_threshold(self, client, synthetic_prices):
        resp = client.get("/api/analytics/anomalies?threshold=5.0")
        data = resp.get_json()
        # Con z > 5, es posible que el pico no se detecte
        assert data["thresholdZ"] == 5.0

    def test_empty(self, client):
        resp = client.get("/api/analytics/anomalies")
        data = resp.get_json()
        assert data["count"] == 0
        assert data["anomalies"] == []


class TestMetricsEndpoint:
    """/metrics Prometheus endpoint."""

    def test_metrics_returns_text(self, client):
        resp = client.get("/metrics")
        assert resp.status_code == 200
        assert b"analytics_requests_total" in resp.data

    def test_metrics_after_request(self, client, synthetic_prices):
        client.get("/health")
        resp = client.get("/metrics")
        assert b"analytics_requests_total" in resp.data


class TestErrorFormat:
    """Verifica la forma de las respuestas de error JSON."""

    def test_error_shape(self, client):
        resp = client.get("/api/analytics/weekday-averages?days=abc")
        data = resp.get_json()
        assert "error" in data
        assert "code" in data["error"]
        assert "message" in data["error"]
