"""Tests de estimación del ahorro."""

from app.services.savings_estimator import estimate_savings, ASSUMPTIONS


class TestSavingsEstimate:
    """Verifica la estimación del ahorro con datos sintéticos."""

    def test_returns_expected_keys(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        assert result["userId"] == 1
        assert "periodDays" in result
        assert "totalSavingsEur" in result
        assert "bestHour" in result
        assert "worstHour" in result
        assert "breakdown" in result
        assert "assumptions" in result

    def test_savings_are_non_negative(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        # El ahorro debería ser >= 0 porque desplazar a horas más baratas siempre ayuda
        assert result["totalSavingsEur"] >= 0

    def test_best_hour_is_cheap(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        # La mejor hora debería estar en el rango nocturno (0-6)
        assert result["bestHour"] is not None
        assert 0 <= result["bestHour"] <= 23

    def test_worst_hour_is_expensive(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        assert result["worstHour"] is not None
        assert 0 <= result["worstHour"] <= 23

    def test_assumptions_documented(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        assert len(result["assumptions"]) == len(ASSUMPTIONS)
        for assumption in result["assumptions"]:
            assert isinstance(assumption, str)
            assert len(assumption) > 0

    def test_breakdown_contains_types(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=1)
        types = {b["type"] for b in result["breakdown"]}
        assert "laundry" in types
        assert "dishwasher" in types
        assert "ev_charging" in types

    def test_empty_session(self, db_session):
        result = estimate_savings(db_session, user_id=42)
        assert result["totalSavingsEur"] == 0.0
        assert result["bestHour"] is None
        assert result["worstHour"] is None

    def test_user_id_none(self, db_session, synthetic_prices):
        result = estimate_savings(db_session, user_id=None)
        assert result["userId"] is None
        assert result["totalSavingsEur"] >= 0
