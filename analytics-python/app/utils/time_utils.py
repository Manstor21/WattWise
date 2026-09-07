"""Time-zone helpers and slot math for Europe/Madrid (CET/CEST)."""

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

# PVPC publishes 48 half-hour slots per day
SLOT_MINUTES_30 = 30
SLOT_MINUTES_60 = 60
SLOTS_PER_DAY_30 = 48
SLOTS_PER_DAY_60 = 24


def utc_to_madrid(dt_utc: datetime) -> datetime:
    """Convert a naive or aware UTC datetime to Europe/Madrid."""
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.astimezone(MADRID_TZ)


def madrid_now() -> datetime:
    """Current time in Europe/Madrid."""
    return datetime.now(MADRID_TZ)


def weekday_index_madrid(dt_utc: datetime) -> int:
    """Return 0=Monday..6=Sunday for a UTC timestamp in Madrid zone."""
    return utc_to_madrid(dt_utc).weekday()


def weekday_label_es(index: int) -> str:
    """Spanish label for a weekday index."""
    return WEEKDAY_LABELS_ES[index]


def weekday_label_en(index: int) -> str:
    """English label for a weekday index."""
    return WEEKDAY_LABELS_EN[index]


def slot_from_hour(hour: int, slot_minutes: int = SLOT_MINUTES_30) -> int:
    """Return the slot index within a day for a given hour (0-23).

    For 30-min slots: hour 0 → slot 0, hour 1 → slot 2, etc.
    For 60-min slots: hour 0 → slot 0, hour 1 → slot 1, etc.
    """
    return (hour * 60) // slot_minutes


def iso_format_utc(dt_utc: datetime) -> str:
    """Format a datetime as ISO-8601 with UTC 'Z' suffix."""
    if dt_utc.tzinfo is None:
        dt_utc = dt_utc.replace(tzinfo=timezone.utc)
    return dt_utc.isoformat()


def parse_period_days(period_str: str) -> int:
    """Parse a period string like '30d' or '7d' into integer days."""
    s = period_str.strip().lower()
    if s.endswith("d"):
        return int(s[:-1])
    return int(s)
