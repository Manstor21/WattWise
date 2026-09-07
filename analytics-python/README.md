# WattWise Analytics — Python Microservice

Statistical analytics over PVPC hourly electricity prices (Spain) for the
WattWise optimizer. Consumes the `PRICE_RECORD` table produced by the Spring
Boot backend and exposes computed statistics over REST.

This is a **portfolio module** — clean, well-documented code with deterministic
synthetic-data tests.

## Stack

- Python 3.11+ (runs locally on 3.14)
- Flask (app factory + blueprints)
- Pandas / NumPy (time-series analysis)
- SQLAlchemy 2.x (read-only ORM, `Mapped`/`mapped_column` style)
- prometheus_client (metrics)
- pytest (tests)

## Project structure

```
analytics-python/
├── app/
│   ├── main.py          # create_app() factory, /metrics, metrics middleware
│   ├── config.py        # env-driven configuration
│   ├── routes/          # analytics + health blueprints
│   ├── services/        # analytics logic (weekday averages, savings, trend, anomalies)
│   ├── models/          # SQLAlchemy read-only PRICE_RECORD model
│   └── utils/           # time_utils: Europe/Madrid conversion, slot math
├── tests/               # pytest with in-memory SQLite + synthetic data
├── requirements.txt
├── pyproject.toml
├── Dockerfile
└── README.md
```

## Running locally

```bash
pip install -r requirements.txt
python -c "from app.main import create_app; app = create_app(); app.run(port=5000)"
```

Or with the Flask CLI:

```bash
export FLASK_APP=app.main        # PowerShell: $env:FLASK_APP="app.main"
export FLASK_ENV=development
flask run --port 5000
```

### Configuration (environment variables)

| Variable        | Default              | Description                                    |
|-----------------|----------------------|------------------------------------------------|
| `DATABASE_URL`  | `sqlite:///wattwise.db` | SQLAlchemy connection string (dev = SQLite) |
| `PORT`          | `5000`               | HTTP port                                     |
| `DAYS_DEFAULT`  | `30`                 | Default window for averages/trend              |
| `FLASK_ENV`     | `development`        | `development` / `testing` / `production`      |

**Production:** point `DATABASE_URL` at SQL Server, e.g.:

```
DATABASE_URL=mssql+pyodbc://USER:PASSWORD@HOST:1433/DB?driver=ODBC+Driver+17+for+SQL+Server
```

Credentials are supplied via the environment only — never hardcode them.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Liveness — `{status: "ok"}` |
| GET | `/ready` | Readiness — checks DB connection |
| GET | `/api/analytics/weekday-averages?days=30` | Average price by weekday (Mon–Sun) over last N days |
| GET | `/api/analytics/savings-estimate?userId=1` | Estimated monthly savings by load-shifting |
| GET | `/api/analytics/trend?period=30d` | Linear-regression trend over daily averages |
| GET | `/api/analytics/anomalies` | Z-score outliers (|z| > 3) over full period |
| GET | `/metrics` | Prometheus metrics (text/plain) |

### Example responses

**weekday-averages**

```json
{
  "days": 30,
  "averages": [
    {"weekday": "Monday", "label": "Lunes", "avgPriceEurPerKwh": 0.132, "min": 0.05, "max": 0.30, "samples": 96}
  ]
}
```

**trend**

```json
{
  "period": "30d",
  "start": "2026-08-01",
  "end": "2026-08-30",
  "slope": 0.000342,
  "direction": "up",
  "pctChange": 4.21,
  "shortLabel": "+4.2% over 30d"
}
```

**anomalies**

```json
{
  "count": 1,
  "thresholdZ": 3.0,
  "anomalies": [
    {"timestamp": "2026-08-15T18:30:00+02:00", "priceEurPerKwh": 0.50, "zScore": 4.2}
  ]
}
```

## Tests

```bash
pytest
```

Tests use an in-memory SQLite database with deterministic synthetic price data
(seed 42) covering all four analytics features plus the REST API. No external
services or testcontainers required.

## Docker

```bash
docker build -t wattwise-analytics .
docker run -p 5000:5000 -e DATABASE_URL=sqlite:///wattwise.db wattwise-analytics
```

## Design notes

- `PRICE_RECORD` is modelled as **read-only**: the table is owned by the Spring
  Boot backend; this service only ever `SELECT`s.
- Timestamps are stored UTC in the DB; weekday grouping and labels are computed
  in the `Europe/Madrid` timezone (handles CEST/DST via `zoneinfo`).
- The **savings model** is a documented, transparent approximation using a
  typical Spanish household profile. `userId` is accepted but not validated
  (this service owns no user data).
- `total_eur_per_kwh` (price including tax) is the field analysed throughout.

## Metrics

Prometheus `/metrics` exports:

- `analytics_requests_total` — counter labelled by `endpoint`, `method`, `status`
- `analytics_request_duration_seconds` — histogram of request latency
