"""Health-check and readiness probes."""

from flask import Blueprint, jsonify, current_app
from sqlalchemy import text

health_bp = Blueprint("health", __name__)


@health_bp.route("/health")
def liveness():
    """Liveness probe — always returns OK if the process is up."""
    return jsonify({"status": "ok"})


@health_bp.route("/ready")
def readiness():
    """Readiness probe — verifies database connectivity."""
    try:
        db = current_app.extensions["sqlalchemy"]
        db.session.execute(text("SELECT 1"))
        return jsonify({"status": "ready"})
    except Exception as exc:
        return jsonify({"status": "not ready", "error": str(exc)}), 503
