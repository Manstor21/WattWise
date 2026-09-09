"""Promedios por día de la semana y estadísticas móviles de precios de la electricidad."""

from typing import Any

import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.price_record import PriceRecord
from app.utils.time_utils import (
    MADRID_TZ,
    WEEKDAY_LABELS_EN,
    WEEKDAY_LABELS_ES,
    utc_to_madrid,
)


def get_weekday_averages(
    session: Session, days: int = 30
) -> dict[str, Any]:
    """Calcula estadísticas de precios agrupadas por día de la semana en los últimos N días.

    Devuelve un dict con el recuento de ``days`` y una lista ``averages`` ordenada
    de lunes a domingo. Solo se incluyen los días de la semana que tienen al menos
    un dato.
    """
    from datetime import datetime, timezone, timedelta

    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    stmt = select(PriceRecord).where(PriceRecord.timestamp >= cutoff)
    rows = session.execute(stmt).scalars().all()

    if not rows:
        return {"days": days, "averages": []}

    # Construir un DataFrame
    records = []
    for r in rows:
        madrid_dt = utc_to_madrid(r.timestamp)
        records.append(
            {
                "weekday": madrid_dt.weekday(),
                "total": r.total_eur_per_kwh,
            }
        )

    df = pd.DataFrame(records)
    grouped = df.groupby("weekday")["total"]

    averages: list[dict[str, Any]] = []
    for wd in sorted(df["weekday"].unique()):
        grp = grouped.get_group(wd)
        averages.append(
            {
                "weekday": WEEKDAY_LABELS_EN[wd],
                "label": WEEKDAY_LABELS_ES[wd],
                "avgPriceEurPerKwh": round(float(grp.mean()), 6),
                "min": round(float(grp.min()), 6),
                "max": round(float(grp.max()), 6),
                "samples": int(len(grp)),
            }
        )

    return {"days": days, "averages": averages}


def get_rolling_stats(
    session: Session, window: int = 7
) -> list[dict[str, Any]]:
    """Devuelve los precios medios diarios con una media móvil sobre *window* días.

    Útil para la visualización de la tendencia. Devuelve una lista ordenada por
    fecha ascendente.
    """
    from datetime import datetime, timezone, timedelta

    stmt = select(PriceRecord).order_by(PriceRecord.timestamp.asc())
    rows = session.execute(stmt).scalars().all()

    if not rows:
        return []

    dates = [utc_to_madrid(r.timestamp).date() for r in rows]
    prices = [r.total_eur_per_kwh for r in rows]

    df = pd.DataFrame({"date": dates, "price": prices})
    daily = df.groupby("date")["price"].mean().reset_index()
    daily["rolling"] = daily["price"].rolling(window=window, min_periods=1).mean()

    result: list[dict[str, Any]] = []
    for _, row in daily.iterrows():
        result.append(
            {
                "date": row["date"].isoformat(),
                "avgPrice": round(float(row["price"]), 6),
                "rollingAvg": round(float(row["rolling"]), 6),
            }
        )
    return result
