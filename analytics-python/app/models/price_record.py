"""SQLAlchemy read-only model for the price_records table.

Uses SQLAlchemy 2.x declarative style with Mapped annotations.
The table is owned by the Spring Boot backend (Flyway V1__init.sql); this
service reads only. The table name and columns must match the backend schema
exactly — SQL Server resolves object names according to the database
collation, so a PRICE_RECORD mapping fails against Flyway's price_records.
"""

from datetime import datetime
from typing import Optional

from sqlalchemy import BigInteger, DateTime, Float, Integer, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class PriceRecord(Base):
    """Read-only mapping to the price_records table."""

    __tablename__ = "price_records"

    id: Mapped[int] = mapped_column(
        # INTEGER on SQLite (rowid alias so AUTOINCREMENT works in tests/dev),
        # BIGINT on SQL Server (production schema owned by the backend).
        BigInteger().with_variant(Integer, "sqlite"),
        primary_key=True,
        autoincrement=True,
    )
    timestamp: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    price_eur_per_kwh: Mapped[float] = mapped_column(Float, nullable=False)
    plus_tax_eur_per_kwh: Mapped[Optional[float]] = mapped_column(Float, nullable=True)
    total_eur_per_kwh: Mapped[float] = mapped_column(Float, nullable=False)
    source: Mapped[Optional[str]] = mapped_column(String(16), nullable=True)

    def __repr__(self) -> str:
        return (
            f"<PriceRecord id={self.id} ts={self.timestamp} "
            f"total={self.total_eur_per_kwh}>"
        )
