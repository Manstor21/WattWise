"""Helpers de zonas horarias y matemática de slots para Europe/Madrid (CET/CEST)."""

from datetime import datetime, time, timezone, timedelta
from zoneinfo import ZoneInfo

MADRID_TZ = ZoneInfo("Europe/Madrid")

WEEKDAY_LABELS_ES = {
    0: "Lunes",
    1: "Martes",
    2: "Miércoles",
    3: "Jueves",
    4: "Viernes",
    5: "Sábado",
    6: "Domingo",
}

WEEKDAY_LABELS_EN = {
    0: "Monday",
    1: "Tuesday",
    2: "Wednesday",
    3: "Thursday",
    4: "Friday",
    5: "Saturday",
    6: "Sunday",
}

# PVPC publica 48 slots de media hora por día
SLOT_MINUTES_30 = 30
SLOT_MINUTES_60 = 60
SLOTS_PER_DAY_30 = 48
SLOTS_PER_DAY_60 = 24


def utc_to_madrid(dt_utc: datetime) -> datetime:
    """Convierte un datetime UTC naive o aware a Europe/Madrid."""
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.astimezone(MADRID_TZ)


def madrid_now() -> datetime:
    """Hora actual en Europe/Madrid."""
    return datetime.now(MADRID_TZ)


def weekday_index_madrid(dt_utc: datetime) -> int:
    """Devuelve 0=Lunes..6=Domingo para una marca temporal UTC en la zona de Madrid."""
    return utc_to_madrid(dt_utc).weekday()


def weekday_label_es(index: int) -> str:
    """Etiqueta en español para un índice de día de la semana."""
    return WEEKDAY_LABELS_ES[index]


def weekday_label_en(index: int) -> str:
    """Etiqueta en inglés para un índice de día de la semana."""
    return WEEKDAY_LABELS_EN[index]


def slot_from_hour(hour: int, slot_minutes: int = SLOT_MINUTES_30) -> int:
    """Devuelve el índice del slot dentro de un día para una hora dada (0-23).

    Para slots de 30 min: hora 0 → slot 0, hora 1 → slot 2, etc.
    Para slots de 60 min: hora 0 → slot 0, hora 1 → slot 1, etc.
    """
    return (hour * 60) // slot_minutes


def iso_format_utc(dt_utc: datetime) -> str:
    """Formatea un datetime como ISO-8601 con sufijo UTC 'Z'."""
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.isoformat()


def parse_period_days(period_str: str) -> int:
    """Analiza una cadena de periodo como '30d' o '7d' y la convierte a días enteros."""
    s = period_str.strip().lower()
    if s.endswith("d"):
        return int(s[:-1])
    return int(s)
