"""Servicio de estimación del ahorro.

Proporciona proyecciones mensuales de ahorro basadas en un perfil de consumo
de un *hogar español típico*. El modelo es deliberadamente simple y
transparente: todos los supuestos se documentan en la lista ``assumptions``
que se incluye en cada respuesta.

**Importante:** ``userId`` se acepta como parámetro de consulta para la
compatibilidad de la API, pero NO se valida contra ninguna base de datos de
usuarios (este microservicio no tiene ninguna). Una versión futura podría
consultar perfiles de consumo por usuario.
"""

from typing import Any

import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.price_record import PriceRecord
from app.utils.time_utils import utc_to_madrid

# ---------------------------------------------------------------------------
# Perfil de consumo asumido (hogar español típico, tarifa PVPC)
# Todos los valores están en kWh.
# ---------------------------------------------------------------------------
DAILY_BASE_CONSUMPTION_KWH = 6.0  # nevera, standby, iluminación, etc.
LAUNDRY_KWH = 0.8  # un solo ciclo
LAUNDRY_FREQ_PER_WEEK = 3  # 3 lavados/semana
DISHWASHER_KWH = 1.2
DISHWASHER_FREQ_PER_WEEK = 4
EV_CHARGING_KWH = 10.0  # carga nocturna de un VE pequeño / PHEV
EV_FREQ_PER_WEEK = 5

# Las cargas flexibles son las que un usuario podría desplazar a horas más
# baratas. El modelo asume que el usuario desplaza TODA su carga flexible al
# cuartil más barato de horas dentro de cada día.
FLEXIBLE_LOADS: dict[str, dict[str, float]] = {
    "laundry": {"kwh_per_use": LAUNDRY_KWH, "uses_per_week": LAUNDRY_FREQ_PER_WEEK},
    "dishwasher": {
        "kwh_per_use": DISHWASHER_KWH,
        "uses_per_week": DISHWASHER_FREQ_PER_WEEK,
    },
    "ev_charging": {
        "kwh_per_use": EV_CHARGING_KWH,
        "uses_per_week": EV_FREQ_PER_WEEK,
    },
}

TOTAL_FLEXIBLE_DAILY_KWH = sum(
    v["kwh_per_use"] * v["uses_per_week"] / 7 for v in FLEXIBLE_LOADS.values()
)

ASSUMPTIONS = [
    f"Daily base (non-shiftable) consumption: {DAILY_BASE_CONSUMPTION_KWH} kWh/day",
    f"Laundry: {LAUNDRY_KWH} kWh × {LAUNDRY_FREQ_PER_WEEK}x/week",
    f"Dishwasher: {DISHWASHER_KWH} kWh × {DISHWASHER_FREQ_PER_WEEK}x/week",
    "EV charging: 10.0 kWh × 5x/week (PHEV / small EV)",
    "Flexible loads are shifted to the cheapest quartile (p25) of hours each day",
    "Savings = flexible_kwh × (mean_day_price − p25_day_price)",
    "userId is accepted but NOT validated (no user DB in this service)",
]


def estimate_savings(
    session: Session,
    user_id: int | None = None,
    period_days: int = 30,
) -> dict[str, Any]:
    """Estima el ahorro mensual por desplazar el consumo flexible a horas más baratas.

    Algoritmo:
    1. Obtener todos los registros de precios de los últimos *period_days*.
    2. Agrupar por fecha local de Madrid.
    3. Para cada día, calcular el precio medio y el precio del percentil 25.
    4. Ahorro_por_día = flexible_kwh * (precio_medio - precio_p25).
    5. Agregar a lo largo del periodo.
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
            "userId": user_id,
            "periodDays": period_days,
            "totalSavingsEur": 0.0,
            "bestHour": None,
            "worstHour": None,
            "breakdown": [],
            "assumptions": ASSUMPTIONS,
        }

    # Construir el DataFrame con la hora local de Madrid
    records = []
    for r in rows:
        madrid_dt = utc_to_madrid(r.timestamp)
        records.append(
            {
                "date": madrid_dt.date(),
                "hour": madrid_dt.hour,
                "price": r.total_eur_per_kwh,
            }
        )

    df = pd.DataFrame(records)

    # Estadísticas por hora de todo el periodo (para mejor/peor hora)
    hourly_avg = df.groupby("hour")["price"].mean()
    best_hour = int(hourly_avg.idxmin())
    worst_hour = int(hourly_avg.idxmax())

    # Ahorro por día
    savings_per_day: list[float] = []
    breakdown_items: list[dict[str, Any]] = []

    for date_val, day_df in df.groupby("date"):
        day_prices = day_df["price"]
        mean_price = float(day_prices.mean())
        p25_price = float(day_prices.quantile(0.25))
        saving = TOTAL_FLEXIBLE_DAILY_KWH * (mean_price - p25_price)
        savings_per_day.append(saving)
        breakdown_items.append(
            {
                "date": date_val.isoformat(),
                "meanPrice": round(mean_price, 6),
                "p25Price": round(p25_price, 6),
                "savingEur": round(saving, 4),
            }
        )

    total_savings = round(sum(savings_per_day), 4)

    # Construir el desglose por tipo de carga
    type_breakdown: list[dict[str, Any]] = []
    for name, spec in FLEXIBLE_LOADS.items():
        daily_kwh = spec["kwh_per_use"] * spec["uses_per_week"] / 7
        type_breakdown.append(
            {
                "type": name,
                "dailyKwh": round(daily_kwh, 2),
                "sharePct": round(daily_kwh / TOTAL_FLEXIBLE_DAILY_KWH * 100, 1),
            }
        )

    return {
        "userId": user_id,
        "periodDays": period_days,
        "totalSavingsEur": total_savings,
        "bestHour": best_hour,
        "worstHour": worst_hour,
        "breakdown": type_breakdown,
        "assumptions": ASSUMPTIONS,
    }
