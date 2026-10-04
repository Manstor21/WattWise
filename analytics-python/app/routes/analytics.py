"""Endpoints de la API de analítica."""

from flask import Blueprint, jsonify, request, current_app
from sqlalchemy import text

from app.services.price_analytics import get_weekday_averages
from app.services.savings_estimator import estimate_savings
from app.services.trend_analyzer import get_trend, get_anomalies
from app.utils.time_utils import parse_period_days
from app.auth import require_auth

analytics_bp = Blueprint("analytics", __name__, url_prefix="/api/analytics")


def _get_session():
    """Obtiene una sesión SQLAlchemy del contexto actual de la app."""
    db = current_app.extensions["sqlalchemy"]
    return db.session


@analytics_bp.route("/weekday-averages")
@require_auth
def weekday_averages():
    days_param = request.args.get("days", current_app.config.get("DAYS_DEFAULT", 30))
    try:
        days = int(days_param)
    except (TypeError, ValueError):
        return (
            jsonify({"error": {"code": "BAD_REQUEST", "message": "days must be an integer"}}),
            400,
        )

    if days < 1:
        return (
            jsonify({"error": {"code": "BAD_REQUEST", "message": "days must be >= 1"}}),
            400,
        )

    session = _get_session()
    result = get_weekday_averages(session, days=days)

    if not result["averages"]:
        return (
            jsonify({"error": {"code": "NOT_FOUND", "message": "No price data for the requested period"}}),
            404,
        )

    return jsonify(result)


@analytics_bp.route("/savings-estimate")
@require_auth
def savings_estimate():
    user_id_param = request.args.get("userId")
    user_id = None
    if user_id_param is not None:
        try:
            user_id = int(user_id_param)
        except (TypeError, ValueError):
            return (
                jsonify({"error": {"code": "BAD_REQUEST", "message": "userId must be an integer"}}),
                400,
            )

    session = _get_session()
    result = estimate_savings(session, user_id=user_id)
    return jsonify(result)


@analytics_bp.route("/trend")
@require_auth
def trend():
    period_param = request.args.get("period", "30d")
    try:
        period_days = parse_period_days(period_param)
    except (TypeError, ValueError):
        return (
            jsonify({"error": {"code": "BAD_REQUEST", "message": "Invalid period format. Use e.g. '30d' or '7'."}}),
            400,
        )

    if period_days < 1:
        return (
            jsonify({"error": {"code": "BAD_REQUEST", "message": "period must be >= 1 day"}}),
            400,
        )

    session = _get_session()
    result = get_trend(session, period_days=period_days)
    return jsonify(result)


@analytics_bp.route("/anomalies")
@require_auth
def anomalies():
    threshold_param = request.args.get("threshold", "3.0")
    try:
        threshold = float(threshold_param)
    except (TypeError, ValueError):
        return (
            jsonify({"error": {"code": "BAD_REQUEST", "message": "threshold must be a number"}}),
            400,
        )

    session = _get_session()
    result = get_anomalies(session, threshold_z=threshold)
    return jsonify(result)