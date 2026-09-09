"""Modelo SQLAlchemy de solo lectura para la tabla price_records.

Usa el estilo declarativo de SQLAlchemy 2.x (anotaciones ``Mapped``).
La tabla pertenece al backend Spring Boot (Flyway V1__init.sql); este
servicio solo realiza lecturas. El nombre de la tabla y las columnas deben
coincidir exactamente con el esquema del backend — SQL Server resuelve los
nombres de los objetos según la collation de la base de datos, por lo que un
mapeo PRICE_RECORD falla frente a price_records de Flyway.
"""

from datetime import datetime
from typing import Optional

from sqlalchemy import BigInteger, DateTime, Float, Integer, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class PriceRecord(Base):
    """Mapeo de solo lectura a la tabla price_records."""

    __tablename__ = "price_records"

    id: Mapped[int] = mapped_column(
        # INTEGER en SQLite (alias de rowid para que AUTOINCREMENT funcione en
        # pruebas/desarrollo),
        # BIGINT en SQL Server (esquema de producción gestionado por el backend).
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
