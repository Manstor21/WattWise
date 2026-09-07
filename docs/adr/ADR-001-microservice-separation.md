# ADR-001: Analytics as a separate Python microservice

**Status:** Accepted (2026-01-12)

## Context

The recommendation engine in the backend gives concrete "run the washer at 03:00" answers, but early on we wanted the system to also answer "what's the typical price of a Tuesday morning?" and "how much did you save last month?". Those are statistical questions: weekday averages, rolling trends, savings estimates, anomaly detection. They are CPU-heavy, batch-oriented, and full of time-series math that is much easier to write in Python with `pandas` than in Java. They also evolve independently of the REST API — a new dashboard chart shouldn't force a redeploy of the price ingestion pipeline.

## Decision

We split analytics into its own microservice, `analytics-python/`, built with Flask and SQLAlchemy, exposing endpoints under `/api/analytics/*`. It reads directly from SQL Server — the same `price_records` table the backend writes — and serves `/api/analytics/weekday-averages`, `/api/analytics/savings-estimate`, `/api/analytics/trend`, and `/api/analytics/anomalies` (z-score > 3). The backend proxies select endpoints (e.g. `GET /api/analytics/savings`) so clients keep talking to a single port.

## Consequences

Positive:

- `pandas` is the right tool for rolling windows and weekly aggregation; reimplementing that in Java would mean pulling in a heavier statistics stack with less community support.
- Six-month re-aggregations can run and scale independently of the request path (`PriceController`, recommendation engine) without starving the API's HikariCP pool.
- The pipeline and the analysis move at different speeds — a Python contributor can ship a new trend endpoint without touching the Spring Boot deploy cycle.

We accepted: a second runtime to patch and monitor, and a network hop on proxied analytics calls — mitigated by putting both services on the same Docker network (`wattwise-net`, sub-millisecond internal latency). Data consistency rests on both services sharing SQL Server as the single source of truth; the analytics model (`models/price_record.py`) is read-only by design.