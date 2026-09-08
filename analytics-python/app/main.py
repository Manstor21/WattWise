"""Flask application factory."""

import time as _time

from flask import Flask, request
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, scoped_session

from app.config import config_by_name
from app.models.price_record import Base


REQUEST_COUNT = Counter(
    "analytics_requests_total",
    "Total analytics HTTP requests",
    ["endpoint", "method", "status"],
)

REQUEST_LATENCY = Histogram(
    "analytics_request_duration_seconds",
    "Request latency in seconds",
    ["endpoint"],
    buckets=(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0),
)


def create_app(config_name: str | None = None) -> Flask:
    """Create and configure the Flask application.

    Parameters
    ----------
    config_name : str, optional
        One of ``development``, ``testing``, ``production``.
        Defaults to the ``FLASK_ENV`` env var or ``development``.
    """
    import os

    if config_name is None:
        config_name = os.getenv("FLASK_ENV", "development")

    app = Flask(__name__)
    app.config.from_object(config_by_name[config_name])

    # --- Database setup ---
    engine = create_engine(
        app.config["DATABASE_URL"],
        echo=False,
        pool_pre_ping=True,
    )
    session_factory = sessionmaker(bind=engine)
    db_session = scoped_session(session_factory)

    # Store engine/session on app for use in routes
    app.extensions["sqlalchemy"] = type(
        "SQLAlchemyProxy", (), {"session": db_session, "engine": engine}
    )()

    # Create the local SQLite schema for development/testing only. In
    # production the schema is owned by the Spring backend via Flyway
    # (V1__init.sql creates price_records); creating PRICE_RECORD here would
    # race with Flyway and make the backend refuse to migrate ("non-empty
    # schema, no history table").
    if config_name != "production":
        Base.metadata.create_all(bind=engine)

    # --- Prometheus metrics middleware ---
    @app.before_request
    def _start_timer():
        request._prom_start = _time.perf_counter()

    @app.after_request
    def _record_metrics(response):
        endpoint = request.endpoint or request.path
        method = request.method
        status = str(response.status_code)

        REQUEST_COUNT.labels(endpoint=endpoint, method=method, status=status).inc()

        if hasattr(request, "_prom_start"):
            duration = _time.perf_counter() - request._prom_start
            REQUEST_LATENCY.labels(endpoint=endpoint).observe(duration)

        return response

    @app.teardown_appcontext
    def _shutdown_session(exc=None):
        db_session.remove()

    # --- /metrics endpoint ---
    @app.route("/metrics")
    def metrics():
        return generate_latest(), 200, {"Content-Type": CONTENT_TYPE_LATEST}

    # --- Register blueprints ---
    from app.routes.health import health_bp
    from app.routes.analytics import analytics_bp

    app.register_blueprint(health_bp)
    app.register_blueprint(analytics_bp)

    return app
