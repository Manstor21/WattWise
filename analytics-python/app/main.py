"""Fábrica de la aplicación Flask."""

import time as _time

from flask import Flask, request
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, scoped_session

from app.config import config_by_name
from app.models.price_record import Base
from app.auth import init_auth


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
    """Crea y configura la aplicación Flask.

    Parámetros
    ----------
    config_name : str, opcional
        Uno de ``development``, ``testing``, ``production``.
        Por defecto toma la variable de entorno ``FLASK_ENV`` o ``development``.
    """
    import os

    if config_name is None:
        config_name = os.getenv("FLASK_ENV", "development")

    app = Flask(__name__)
    app.config.from_object(config_by_name[config_name])

    # Inicializar autenticación
    init_auth(app)

    # --- Configuración de la base de datos ---
    engine = create_engine(
        app.config["DATABASE_URL"],
        echo=False,
        pool_pre_ping=True,
    )
    session_factory = sessionmaker(bind=engine)
    db_session = scoped_session(session_factory)

    # Guardar el engine/session en la app para usarlos en las rutas
    app.extensions["sqlalchemy"] = type(
        "SQLAlchemyProxy", (), {"session": db_session, "engine": engine}
    )()

    # Crear el esquema local de SQLite solo para desarrollo/pruebas. En
    # producción el esquema lo gestiona el backend de Spring mediante Flyway
    # (V1__init.sql crea price_records); crear PRICE_RECORD aquí competiría
    # con Flyway y haría que el backend rechazara la migración ("non-empty
    # schema, no history table").
    if config_name != "production":
        Base.metadata.create_all(bind=engine)

    # --- Middleware de métricas Prometheus ---
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

    # --- Endpoint /metrics ---
    @app.route("/metrics")
    def metrics():
        return generate_latest(), 200, {"Content-Type": CONTENT_TYPE_LATEST}

    # --- Registrar blueprints ---
    from app.routes.health import health_bp
    from app.routes.analytics import analytics_bp

    app.register_blueprint(health_bp)
    app.register_blueprint(analytics_bp)

    return app