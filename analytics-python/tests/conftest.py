"""Fixtures de test compartidos — SQLite en memoria con datos sintéticos de precios."""

import numpy as np
import pytest
from datetime import datetime, timedelta, timezone

from app.main import create_app
from app.models.price_record import Base
from app.models.price_record import PriceRecord
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker


@pytest.fixture()
def app():
    """Crea una app Flask de prueba respaldada por SQLite en memoria."""
    application = create_app("testing")

    # Sobrescribir el engine de la BD para que las rutas usen la misma BD en memoria
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    TestSession = sessionmaker(bind=engine)

    # Monkey-patch del proxy para que todas las rutas usen nuestra sesión de test
    from unittest.mock import MagicMock

    mock_proxy = MagicMock()
    mock_proxy.session = TestSession()
    mock_proxy.engine = engine
    application.extensions["sqlalchemy"] = mock_proxy

    yield application


@pytest.fixture()
def client(app):
    """Cliente de pruebas Flask."""
    return app.test_client()


@pytest.fixture()
def db_session(app):
    """Sesión SQLAlchemy para manipulación directa de la BD en tests."""
    return app.extensions["sqlalchemy"].session


@pytest.fixture(autouse=True)
def _clean_db(db_session):
    """Realiza rollback y limpieza entre tests."""
    yield
    db_session.rollback()
    # Eliminar todas las filas para mantener la BD en memoria limpia
    db_session.query(PriceRecord).delete()
    db_session.commit()


@pytest.fixture()
def synthetic_prices(db_session):
    """Inserta 30 días de datos sintéticos deterministas de estilo PVPC.

    - Media ~0.12 €/kWh con patrón diario realista (barato de noche, caro en
      horas pico 13-21).
    - Una anomalía clara en el día 15 a las 18h (el precio sube a 0.50).
    - Semilla 42 para reproducibilidad.
    """
    rng = np.random.RandomState(42)

    base_date = datetime(2026, 8, 1, 0, 0, 0, tzinfo=timezone.utc)

    records: list[PriceRecord] = []
    for day in range(30):
        for half_hour in range(48):
            ts = base_date + timedelta(days=day, minutes=30 * half_hour)

            # Simular la curva PVPC: barato durante la noche, caro en horas pico 13-21
            hour = half_hour // 2
            if 0 <= hour < 7:
                base = 0.06
            elif 7 <= hour < 10:
                base = 0.10
            elif 10 <= hour < 13:
                base = 0.13
            elif 13 <= hour < 21:
                base = 0.18
            else:
                base = 0.08

            noise = rng.normal(0, 0.005)
            price = round(max(0.0, base + noise), 6)

            # Inyectar anomalía en el día 15, slot 36 (hora 18)
            if day == 15 and half_hour == 36:
                price = 0.50

            tax = round(price * 0.21, 6)  # 21% tipo IVA
            total = round(price + tax, 6)

            records.append(
                PriceRecord(
                    timestamp=ts,
                    price_eur_per_kwh=price,
                    plus_tax_eur_per_kwh=round(price * 1.05, 6),
                    total_eur_per_kwh=total,
                    source="ESIOS",
                )
            )

    db_session.add_all(records)
    db_session.commit()
    return records
