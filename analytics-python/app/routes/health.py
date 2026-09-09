"""Sondas de health-check y de readiness."""

from flask import Blueprint, jsonify, current_app
from sqlalchemy import text

health_bp = Blueprint("health", __name__)


@health_bp.route("/health")
def liveness():
    """Sonda de liveness — devuelve OK siempre que el proceso esté activo."""
    return jsonify({"status": "ok"})


@health_bp.route("/ready")
def readiness():
    """Sonda de readiness — verifica la conectividad con la base de datos."""
    try:
        db = current_app.extensions["sqlalchemy"]
        db.session.execute(text("SELECT 1"))
        return jsonify({"status": "ready"})
    except Exception as exc:
        return jsonify({"status": "not ready", "error": str(exc)}), 503
