"""Tests de promedios por día de la semana y estadísticas móviles."""

from app.services.price_analytics import get_weekday_averages, get_rolling_stats


class TestWeekdayAverages:
    """Verifica el cálculo de promedios por día de la semana con datos sintéticos."""

    def test_returns_7_days(self, db_session, synthetic_prices):
        result = get_weekday_averages(db_session, days=30)
        assert "averages" in result
        assert len(result["averages"]) == 7

    def test_ordered_monday_to_sunday(self, db_session, synthetic_prices):
        result = get_weekday_averages(db_session, days=30)
        weekdays = [a["weekday"] for a in result["averages"]]
        assert weekdays == [
            "Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday",
        ]

    def test_each_day_has_spanish_label(self, db_session, synthetic_prices):
        result = get_weekday_averages(db_session, days=30)
        for entry in result["averages"]:
            assert "label" in entry
            assert "avgPriceEurPerKwh" in entry
            assert "min" in entry
            assert "max" in entry
            assert "samples" in entry
            assert entry["min"] <= entry["avgPriceEurPerKwh"] <= entry["max"]
            assert entry["samples"] > 0

    def test_days_parameter_reflected(self, db_session, synthetic_prices):
        result = get_weekday_averages(db_session, days=7)
        assert result["days"] == 7

    def test_empty_session(self, db_session):
        result = get_weekday_averages(db_session, days=30)
        assert result["averages"] == []
        assert result["days"] == 30

    def test_averages_are_positive(self, db_session, synthetic_prices):
        result = get_weekday_averages(db_session, days=30)
        for entry in result["averages"]:
            assert entry["avgPriceEurPerKwh"] > 0


class TestRollingStats:
    """Verifica el cálculo de estadísticas móviles."""

    def test_rolling_returns_list(self, db_session, synthetic_prices):
        result = get_rolling_stats(db_session, window=7)
        assert isinstance(result, list)
        assert len(result) > 0

    def test_rolling_fields(self, db_session, synthetic_prices):
        result = get_rolling_stats(db_session, window=7)
        for entry in result:
            assert "date" in entry
            assert "avgPrice" in entry
            assert "rollingAvg" in entry

    def test_rolling_empty(self, db_session):
        result = get_rolling_stats(db_session, window=7)
        assert result == []
