# ADR-003: Resilience against the ESIOS API (no SLA, no guarantees)

**Status:** Accepted (2026-02-09)

## Context

REE's ESIOS API (`datos/mercados/precios-mercados-tiempo-real`) is a public endpoint with no SLA. Tomorrow's prices are supposed to appear in the evening — occasionally they don't. Downstream, every recommendation we serve depends on a full 96-slot day being ingested, and the traffic-light classifier (`percentile-green-max: 0.33`, `percentile-red-min: 0.67`) assumes a complete day: one missing hour skews the percentiles. So the ingestion path in `PriceFetchJob` / `EsirosClientService` is treated as hostile-environment code, not happy-path code.

## Decision

We fail softly and keep the last good data:

- Three attempts with exponential backoff (`max-attempts: 3`, `initial-backoff-ms: 2000`, `max-backoff-ms: 30000`) and explicit connect/read timeouts (`10000ms` / `30000ms`) so a hung response can't pin the scheduler.
- If ESIOS stays down, we keep the last successfully fetched prices, mark them `STALE`, show a "prices may be outdated" banner in the UI, and fire an alert.
- Late publication is handled as a schedule, not a crisis: main run at ~20:15 (cron `0 15 20 * * *`), catch-up retries at 21:00 and 22:00, and an alert if D+1 is still missing.
- Data gaps are tolerated: the classifier degrades gracefully on missing slots rather than throwing, and prices can enter with `source = 'MANUAL'` (desktop-admin CSV import / price correction), giving operators a backstop that never touches ESIOS.

Prometheus tracks all of it: `esiros_fetch_total{status}`, `esiros_fetch_duration_seconds`; Grafana warns when the 24h failure rate passes 50%.

## Consequences

Positive: the system survives API outages with visible, honest degradation and a manual backstop; a single missing slot can't poison the traffic-light logic for the whole day.

Negative: we never get real-time prices — ESIOS offers no streaming, so this is polling by design (the scheduler runs every 2h) — and stale data remains a possibility, bounded by the retry schedule and surfaced to users. Acceptable because PVPC is a day-ahead market where two-hour freshness is plenty.