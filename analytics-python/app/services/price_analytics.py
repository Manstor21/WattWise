"""Weekday averages and rolling statistics for electricity prices."""

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
    """Compute price statistics grouped by weekday over the last N days.

    Returns a dict with ``days`` count and ``averages`` list ordered Monday→Sunday.
    Only weekdays that have at least one data point are included.
    """
    from datetime import datetime, timezone, timedelta

    cutoff = datetime.now(timezone.utc) - timedelta(days=days)
    stmt = select(PriceRecord).where(PriceRecord.timestamp >= cutoff)
    rows = session.execute(stmt).scalars().all()

    if not rows:
        return {"days": days, "averages": []}

    # Build a DataFrame
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
    """Return daily mean prices with a rolling average over *window* days.

    Useful for trend visualisation. Returns list sorted by date ascending.
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
