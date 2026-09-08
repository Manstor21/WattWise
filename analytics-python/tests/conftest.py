"""Shared test fixtures — in-memory SQLite with synthetic price data."""

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
    """Create a test Flask app backed by in-memory SQLite."""
    application = create_app("testing")

    # Override the DB engine so routes hit the same in-memory DB
    from sqlalchemy.pool import StaticPool

    engine = create_engine(
        "sqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    TestSession = sessionmaker(bind=engine)

    # Monkey-patch the proxy so all routes use our test session
    from unittest.mock import MagicMock

    mock_proxy = MagicMock()
    mock_proxy.session = TestSession()
    mock_proxy.engine = engine
    application.extensions["sqlalchemy"] = mock_proxy

    yield application


@pytest.fixture()
def client(app):
    """Flask test client."""
    return app.test_client()


@pytest.fixture()
def db_session(app):
    """SQLAlchemy session for direct DB manipulation in tests."""
    return app.extensions["sqlalchemy"].session


@pytest.fixture(autouse=True)
def _clean_db(db_session):
    """Roll back and clean between tests."""
    yield
    db_session.rollback()
    # Delete all rows to keep in-memory DB clean
    db_session.query(PriceRecord).delete()
    db_session.commit()


@pytest.fixture()
def synthetic_prices(db_session):
    """Insert 30 days of deterministic synthetic PVPC-style price data.

    - Mean ~0.12 €/kWh with realistic daily pattern (cheap at night, expensive
      at peak hours 13-21).
    - One clear anomaly on day 15 at hour 18 (price spikes to 0.50).
    - Seed 42 for reproducibility.
    """
    rng = np.random.RandomState(42)

    base_date = datetime(2026, 8, 1, 0, 0, 0, tzinfo=timezone.utc)

    records: list[PriceRecord] = []
    for day in range(30):
        for half_hour in range(48):
            ts = base_date + timedelta(days=day, minutes=30 * half_hour)

            # Simulate PVPC curve: cheap overnight, expensive peak 13-21
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

            # Inject anomaly on day 15, slot 36 (hour 18)
            if day == 15 and half_hour == 36:
                price = 0.50

            tax = round(price * 0.21, 6)  # 21% IVA-like
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
