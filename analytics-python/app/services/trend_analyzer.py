"""Análisis de tendencia y detección de anomalías en los precios de la electricidad."""

from typing import Any

import numpy as np
import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.price_record import PriceRecord
from app.utils.time_utils import utc_to_madrid


def get_trend(session: Session, period_days: int = 30) -> dict[str, Any]:
    """Calcula una tendencia de regresión lineal simple sobre los precios medios diarios.

    Devuelve la pendiente (€/kWh por día), la dirección, el cambio porcentual y
    una etiqueta corta legible para humanos.
    """
    from datetime import datetime, timezone, timedelta

    cutoff = datetime.now(timezone.utc) - timedelta(days=period_days)
    stmt = (
        select(PriceRecord)
        .where(PriceRecord.timestamp >= cutoff)
        .order_by(PriceRecord.timestamp.asc())
    )
    rows = session.execute(stmt).scalars().all()

    if not rows:
        return {
            "period": f"{period_days}d",
            "start": None,
            "end": None,
            "slope": 0.0,
            "direction": "flat",
            "pctChange": 0.0,
            "shortLabel": "No data",
        }

    dates = [utc_to_madrid(r.timestamp).date() for r in rows]
    prices = [r.total_eur_per_kwh for r in rows]

    df = pd.DataFrame({"date": dates, "price": prices})
    daily = df.groupby("date")["price"].mean().reset_index()
    daily = daily.sort_values("date").reset_index(drop=True)

    if len(daily) < 2:
        row = daily.iloc[0]
        return {
            "period": f"{period_days}d",
            "start": row["date"].isoformat(),
            "end": row["date"].isoformat(),
            "slope": 0.0,
            "direction": "flat",
            "pctChange": 0.0,
            "shortLabel": "Single day of data",
        }

    x = np.arange(len(daily), dtype=float)
    y = daily["price"].values.astype(float)

    # Regresión lineal: y = pendiente * x + intersección
    coeffs = np.polyfit(x, y, 1)
    slope = float(coeffs[0])

    first_price = float(y[0])
    last_price = float(y[-1])
    pct_change = ((last_price - first_price) / first_price * 100) if first_price != 0 else 0.0

    # Umbrales de dirección: pendiente normalizada respecto al precio medio
    mean_price = float(y.mean())
    if mean_price != 0:
        normalised_slope = slope / mean_price
    else:
        normalised_slope = 0.0

    if normalised_slope > 0.01:
        direction = "up"
        short_label = f"+{pct_change:.1f}% over {period_days}d"
    elif normalised_slope < -0.01:
        direction = "down"
        short_label = f"{pct_change:.1f}% over {period_days}d"
    else:
        direction = "flat"
        short_label = f"~{pct_change:.1f}% over {period_days}d"

    return {
        "period": f"{period_days}d",
        "start": daily["date"].iloc[0].isoformat(),
        "end": daily["date"].iloc[-1].isoformat(),
        "slope": round(slope, 8),
        "direction": direction,
        "pctChange": round(pct_change, 2),
        "shortLabel": short_label,
    }


def get_anomalies(
    session: Session, threshold_z: float = 3.0
) -> dict[str, Any]:
    """Detecta anomalías de precios usando la puntuación z sobre todo el periodo disponible.

    Devuelve todos los puntos de datos donde |z| > *threshold_z*.
    """
    stmt = (
        select(PriceRecord)
        .order_by(PriceRecord.timestamp.asc())
    )
    rows = session.execute(stmt).scalars().all()

    if not rows:
        return {"count": 0, "thresholdZ": threshold_z, "anomalies": []}

    prices = np.array([r.total_eur_per_kwh for r in rows], dtype=float)
    mean = prices.mean()
    std = prices.std(ddof=0)

    if std == 0:
        return {"count": 0, "thresholdZ": threshold_z, "anomalies": []}

    z_scores = (prices - mean) / std
    anomalies: list[dict[str, Any]] = []

    for i, z in enumerate(z_scores):
        if abs(z) > threshold_z:
            anomalies.append(
                {
                    "timestamp": utc_to_madrid(rows[i].timestamp).isoformat(),
                    "priceEurPerKwh": round(float(rows[i].total_eur_per_kwh), 6),
                    "zScore": round(float(z), 4),
                }
            )

    return {
        "count": len(anomalies),
        "thresholdZ": threshold_z,
        "anomalies": anomalies,
    }
