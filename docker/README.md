# WattWise — Docker Development & Deployment

Containerised stack for the WattWise energy-price platform: reverse proxy + SPA,
Spring Boot API, SQL Server, Python analytics microservice, and a full
Prometheus + Grafana observability layer.

Run everything from the **repository root** (the compose build contexts use `..`),
for example `docker compose -f docker/docker-compose.yml ...`.

---

## Quick start

```bash
# 1. (optional, recommended) create your environment file
cp docker/.env.example docker/.env
#    edit docker/.env and set strong DB_PASSWORD / JWT_SECRET / GRAFANA_ADMIN_PASSWORD

# 2. start the full production-like stack
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d --build

# 3. check status
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml ps

# 4. tear down
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml down
```

The first SQL Server boot initialises a few minutes. `docker compose ps` shows each
container, and the `healthcheck` column turns green once ready.

### Default credentials (change in docker/.env!)

| Service                   | URL                | Username / Password                 |
| ------------------------- | ------------------ | ----------------------------------- |
| Web app (nginx)           | http://localhost:8080 | n/a (public)                      |
| Backend API (nginx proxied) | http://localhost:8080/api | n/a (prod profile expects JWT auth) |
| Spring Boot Actuator      | http://localhost:8080/actuator/health | public health endpoints |
| Analytics API             | http://localhost:5001 | public /health                    |
| Prometheus                | http://localhost:9090 | public                             |
| Grafana                   | http://localhost:3000 | `admin` / `admin` (default!)       |
| SQL Server                | localhost:1433        | `sa` / see `SA_PASSWORD`           |

> Change `GRAFANA_ADMIN_PASSWORD` and the DB passwords before any real deployment.

---

## Ports

| Port | Service                                  | Internal port |
| ---- | ---------------------------------------- | ------------- |
| 8080 | nginx (static SPA + reverse proxy)       | 80            |
| 5001 | analytics-python (Flask + gunicorn)      | 5000          |
| 9090 | Prometheus                               | 9090          |
| 3000 | Grafana                                  | 3000          |
| 1433 | SQL Server                               | 1433          |
| —    | backend (Spring Boot)                    | 8080 internal only |

The backend is deliberately **not** published to the host; reach it through nginx
at `http://localhost:8080/api/...` and `/actuator/...`.

---

## Compose files

| File                                 | Purpose                                                        |
| ------------------------------------ | -------------------------------------------------------------- |
| `docker/docker-compose.yml`            | Base stack: all six services on the `wattwise-net` network.  |
| `docker/docker-compose.dev.yml`        | Dev overlays: SQLite backend/dev profile, sql-server disabled, port 8080 published, state in `./data`. |
| `docker/docker-compose.prod.yml`       | Production overlays: SQL Server dependencies, resource limits, `restart: always`. |

### Development without SQL Server

```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up -d --build
```

The dev overlay:
- disables `sql-server` (compose profile `sql-server`, opt-in with `--profile sql-server`),
- sets the backend to the `dev` Spring profile with **SQLite** (`./data/wattwise.db`),
- points analytics at a SQLite file (`./data/wattwise-analytics.db`),
- publishes backend `8080` so you can hit Swagger/Actuator directly.

To include SQL Server in dev: append `--profile sql-server` to the `up` command.

### Production

```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d --build
```

Adds `restart: always` and CPU/memory limits to every service, and enforces
start-up ordering so the backend and analytics wait for SQL Server health.

---

## Testing the running stack

```bash
# Backend health (through nginx)
curl -fsS http://localhost:8080/actuator/health

# Analytics health
curl -fsS http://localhost:5001/health

# Login API smoke test (if spring.security is configured on /api)
curl -s -X POST http://localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"..."}'

# Prometheus is scraping both targets
curl -fsS http://localhost:9090/api/v1/targets

# Observable metrics
curl -fsS http://localhost:9090/api/v1/query?query=esiros_fetch_total
curl -fsS http://localhost:9090/api/v1/query?query=price_records_inserted_total

# Grafana dashboards (provisioned automatically)
#   http://localhost:3000  ->  login admin/admin, folder "WattWise"
```

---

## Data & schema ownership

- **SQL Server schema is owned by Flyway** (backend migrations in
  `backend/src/main/resources/db/migration`). The init scripts in
  `docker/sql-server/init-scripts/` create only the database, login and user —
  they never `CREATE TABLE`.
- Dev SQLite databases live under `docker/data/` (git-ignored). Delete the files
  to reset local state.
- Named volumes persist SQL Server data, Prometheus TSDB and Grafana: use
  `docker compose ... down -v` to wipe them.

---

## Observability

Prometheus scrapes every 15s:

| Target                                       | Source                     |
| -------------------------------------------- | -------------------------- |
| `backend:8080/actuator/prometheus`           | Spring Boot Micrometer (incl. `esiros_fetch_total`, `price_records_inserted_total`, `esiros_last_fetch_success_timestamp` — instrumented in `PriceFetchJob`) |
| `analytics-python:5000/metrics`              | prometheus_client (Flask requests/latency) |
| blackbox probes (`nginx`, `sql-server`, `prometheus`, `grafana`) | blackbox-exporter `probe_success` |

Three Grafana dashboards are provisioned on startup (folder “WattWise”):

- **Service Overview** — health for all six services + request / error rates.
- **Price Pipeline** — ESIOS fetch outcomes, records inserted, data freshness.
- **Business Metrics** — PVPC records stored, fetch reliability, analytics volume & p95 latency.

Grafana configuration lives in `docker/grafana/` (datasource uid `prometheus`,
`grafana.ini` enables anonymous view-only access; the admin is `admin` / env `GF_SECURITY_ADMIN_PASSWORD`).

---

## Security notes

- Never commit `docker/.env`. A `.gitignore` at the repository root already
  ignores `.env*`; only `docker/.env.example` is tracked.
- Defaults in the compose file are dev-friendly; set strong secrets in `.env`.
- Grafana admin / SA / DB passwords must be rotated before any shared deployment.

---

## Podman

Compose v2 syntax works with Podman too:

```bash
podman compose -f docker/docker-compose.yml -f docker/docker-compose.prod.yml up -d --build
```

Resource `limits` are honoured by podman-compose equivalents where supported.