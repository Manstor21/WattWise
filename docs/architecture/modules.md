# WattWise — Module Reference

Detailed specification for each module in the WattWise monorepo.

---

## 1. Backend (`backend/`)

### Purpose
Central REST API serving all client applications. Owns business logic for price ingestion, traffic-light classification, recommendation generation, user/appliance management, and alert orchestration.

### Stack
- Java 17, Spring Boot 3.x, Maven
- Spring Security (JWT), Spring Data JPA (Hibernate)
- HikariCP connection pool
- JaCoCo (coverage), JUnit 5 + Mockito (tests)

### Folder Structure

```
backend/
├── src/main/java/com/wattwise/
│   ├── WattWiseApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── JwtConfig.java
│   │   ├── CorsConfig.java
│   │   └── SchedulerConfig.java
│   ├── controller/
│   │   ├── PriceController.java
│   │   ├── UserController.java
│   │   ├── ApplianceController.java
│   │   ├── RecommendationController.java
│   │   └── AlertController.java
│   ├── service/
│   │   ├── PriceService.java
│   │   ├── UserService.java
│   │   ├── ApplianceService.java
│   │   ├── RecommendationService.java
│   │   ├── AlertService.java
│   │   └── EsirosClientService.java
│   ├── scheduler/
│   │   └── PriceFetchJob.java
│   ├── recommendation/
│   │   ├── RecommendationStrategy.java        (interface)
│   │   ├── WashingMachineStrategy.java
│   │   ├── DishwasherStrategy.java
│   │   ├── EvChargingStrategy.java
│   │   ├── GenericApplianceStrategy.java
│   │   └── StrategyFactory.java
│   ├── alert/
│   │   ├── AlertObserver.java                 (interface)
│   │   ├── PriceThresholdObserver.java
│   │   ├── AnomalyObserver.java
│   │   └── AlertManager.java
│   ├── model/
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Appliance.java
│   │   │   ├── PriceRecord.java
│   │   │   ├── AlertPreference.java
│   │   │   └── Recommendation.java
│   │   └── dto/
│   │       ├── PriceDto.java
│   │       ├── RecommendationDto.java
│   │       └── UserDto.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ApplianceRepository.java
│   │   ├── PriceRecordRepository.java
│   │   └── AlertPreferenceRepository.java
│   ├── exception/
│   │   ├── ExternalApiException.java
│   │   ├── ResourceNotFoundException.java
│   │   └── GlobalExceptionHandler.java
│   └── util/
│       ├── TrafficLightClassifier.java
│       └── PriceUtils.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml          (SQLite)
│   ├── application-prod.yml         (SQL Server)
│   └── db/migration/                (Flyway scripts)
├── src/test/java/com/wattwise/
│   ├── controller/
│   ├── service/
│   ├── recommendation/
│   ├── scheduler/
│   └── util/
└── pom.xml
```

### Main Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/prices/today` | Today's 96 price records with traffic-light color |
| `GET` | `/api/prices/tomorrow` | Tomorrow's prices (if published) |
| `GET` | `/api/prices/range?from=&to=` | Historical prices for a date range |
| `POST` | `/api/auth/register` | Register new user |
| `POST` | `/api/auth/login` | Authenticate, return JWT |
| `GET` | `/api/users/{id}` | User profile |
| `GET/POST/PUT/DELETE` | `/api/appliances` | CRUD for user appliances |
| `GET` | `/api/recommendations` | Personalized recommendations for all user appliances |
| `GET` | `/api/recommendations?applianceId=X` | Recommendation for a specific appliance |
| `GET/PUT` | `/api/alerts/preferences` | User alert thresholds |

### Connection to Other Modules
- **Web client** consumes all endpoints via AJAX.
- **Android app** consumes all endpoints via HTTP + JWT.
- **Desktop admin** connects directly to SQL Server (bypasses API for admin ops).
- **Python analytics** reads from the same SQL Server and serves `/api/analytics/*` on its own port (published as `localhost:5001` in compose); the backend does not proxy it.
- **ESIOS API** is consumed by the scheduled job.
- **Prometheus** scrapes `/actuator/prometheus`.

### Tests
- Unit: `TrafficLightClassifier`, `RecommendationStrategy` implementations, `PriceUtils`, service layer with Mockito.
- Integration: `PriceController` endpoint tests with `@WebMvcTest`, repository tests with `@DataJpaTest` + H2/SQLite.
- Contract: All REST endpoints validated against OpenAPI spec (springdoc-openapi).
- Coverage target: ≥70% line coverage (JaCoCo, enforced via the Maven `verify` phase).

---

## 2. Web Dashboard (`web/`)

### Purpose
Browser-based responsive dashboard showing real-time prices, traffic-light classification, and personalized recommendations. Served as static files by Nginx.

### Stack
- HTML5, CSS3 (custom, no framework), vanilla JavaScript (ES6+), jQuery 3.x
- AJAX (fetch API + jQuery `$.ajax`) for async data loading

### Folder Structure

```
web/
├── index.html                  (main dashboard)
├── login.html
├── register.html
├── css/
│   ├── reset.css
│   ├── variables.css           (CSS custom properties: colors, spacing)
│   ├── layout.css              (grid, responsive breakpoints)
│   ├── components.css          (cards, buttons, traffic-light indicators)
│   └── animations.css          (price chart transitions)
├── js/
│   ├── app.js                  (init, router-like page controller)
│   ├── api.js                  (AJAX wrapper: fetch, auth headers, error handling)
│   ├── auth.js                 (JWT storage, login/logout, token refresh)
│   ├── dashboard.js            (price chart, traffic-light grid rendering)
│   ├── recommendations.js      (recommendation cards, appliance management)
│   ├── charts.js               (lightweight canvas chart — no D3 dependency)
│   └── utils.js                (date formatting, currency, helpers)
├── assets/
│   ├── img/
│   │   ├── logo.svg
│   │   └── icons/
│   └── fonts/                  (self-hosted, no Google Fonts dependency)
└── templates/
    └── partials/               (reusable HTML fragments loaded via fetch)
```

### Main Components
- **Price chart:** 24h/96-slot bar chart with color-coded bars (green/amber/red). Canvas-based for performance.
- **Traffic-light grid:** Hourly slots displayed as colored cells, clickable for detail.
- **Recommendation cards:** Per-appliance card showing best window, estimated cost, and savings vs. worst time.
- **Appliance manager:** Add/edit/delete appliances with type, power rating, preferred schedule.
- **Responsive layout:** CSS Grid with breakpoints at 480px, 768px, 1024px.

### Connection to Other Modules
- All data via REST API calls to backend (`:8080` through nginx proxy).
- JWT stored in `localStorage`; sent as `Authorization: Bearer` header.
- No server-side rendering — pure SPA-like static site.

### Tests
- Manual responsive testing (browser devtools).
- JS unit tests (optional, via Jest) for `api.js` and `utils.js`.
- Lighthouse audit for performance/accessibility baseline.

---

## 3. Android App (`android/`)

### Purpose
Native mobile client providing traffic-light price visualization, recommendations, and local notifications for optimal appliance scheduling. Fully functional offline.

### Stack
- Android SDK (Java), minimum SDK 26 (Android 8.0)
- Room (SQLite ORM) for local persistence
- WorkManager for periodic sync + notification scheduling
- NotificationCompat for local notifications
- Retrofit 2 + Gson for REST API communication

### Folder Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/wattwise/android/
│   │   │   ├── WattWiseApp.java
│   │   │   ├── ui/
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── DashboardFragment.java
│   │   │   │   ├── RecommendationsFragment.java
│   │   │   │   ├── AppliancesFragment.java
│   │   │   │   ├── SettingsFragment.java
│   │   │   │   ├── LoginActivity.java
│   │   │   │   └── adapter/
│   │   │   │       ├── PriceAdapter.java
│   │   │   │       └── RecommendationAdapter.java
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── WattWiseDatabase.java
│   │   │   │   │   ├── PriceDao.java
│   │   │   │   │   ├── ApplianceDao.java
│   │   │   │   │   └── RecommendationDao.java
│   │   │   │   ├── remote/
│   │   │   │   │   ├── ApiService.java
│   │   │   │   │   └── AuthInterceptor.java
│   │   │   │   └── repository/
│   │   │   │       ├── PriceRepository.java
│   │   │   │       └── ApplianceRepository.java
│   │   │   ├── notification/
│   │   │   │   ├── PriceCheckWorker.java
│   │   │   │   ├── NotificationHelper.java
│   │   │   │   └── OptimalWindowScheduler.java
│   │   │   └── sync/
│   │   │       ├── SyncWorker.java
│   │   │       └── ConflictResolver.java
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   ├── drawable/
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   ├── src/test/       (unit tests)
│   └── src/androidTest/ (instrumentation tests)
├── build.gradle
└── settings.gradle
```

### Main Components
- **DashboardFragment:** RecyclerView showing hourly price slots with traffic-light colors.
- **RecommendationsFragment:** Best windows per appliance with estimated cost.
- **PriceCheckWorker:** Periodic WorkManager task (every 6 hours) checking prices and scheduling notifications for the next optimal window.
- **SyncWorker:** Uploads pending appliance changes and fetches fresh data when online.
- **ConflictResolver:** Last-write-wins with server timestamps for offline edits.

### Connection to Other Modules
- REST API calls to backend (`/api/prices/*`, `/api/recommendations/*`).
- JWT stored in EncryptedSharedPreferences.
- Offline mode reads from Room (SQLite) — identical schema to server-side PriceRecord/Appliance tables.

### Tests
- Unit: Repository layer, `ConflictResolver`, `PriceCheckWorker` logic.
- Instrumentation: Room DAO CRUD operations, Retrofit API contract tests (MockWebServer).
- UI: Basic Espresso tests for login flow and dashboard rendering.

---

## 4. Analytics Microservice (`analytics-python/`)

### Purpose
Standalone microservice for statistical analysis of electricity prices: averages by weekday, savings projections, trend analysis, and anomaly detection. Exposes results via REST for consumption by the backend or direct dashboard display.

### Stack
- Python 3.11+, Flask
- Pandas for time-series analysis
- SQLAlchemy for SQL Server connectivity
- Pytest for testing

### Folder Structure

```
analytics-python/
├── app/
│   ├── __init__.py
│   ├── main.py                       (Flask/FastAPI app factory)
│   ├── config.py                     (env-based configuration)
│   ├── routes/
│   │   ├── __init__.py
│   │   ├── analytics.py              (/api/analytics/* endpoints)
│   │   └── health.py                 (/health, /ready)
│   ├── services/
│   │   ├── __init__.py
│   │   ├── price_analytics.py        (weekday averages, rolling stats)
│   │   ├── savings_estimator.py      (savings projections per user)
│   │   └── trend_analyzer.py         (price trend detection, anomalies)
│   ├── models/
│   │   ├── __init__.py
│   │   └── price_record.py           (SQLAlchemy model, read-only)
│   └── utils/
│       ├── __init__.py
│       └── time_utils.py             (timezone handling, slot math)
├── tests/
│   ├── test_price_analytics.py
│   ├── test_savings_estimator.py
│   └── conftest.py                   (fixtures, test DB)
├── requirements.txt
├── Dockerfile
└── README.md
```

### Main Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Liveness check |
| `GET` | `/ready` | Readiness check (DB connectivity) |
| `GET` | `/api/analytics/weekday-averages` | Average price by day of week (last N days) |
| `GET` | `/api/analytics/savings-estimate?userId=X` | Estimated monthly savings for user's appliance usage patterns |
| `GET` | `/api/analytics/trend?period=30d` | Price trend over last N days (slope, direction) |
| `GET` | `/api/analytics/anomalies` | Detected price anomalies (z-score > 3) |

### Connection to Other Modules
- Reads directly from SQL Server (shared DB with backend).
- Backend proxies select analytics endpoints for the web/mobile clients.
- Prometheus metrics exposed at `/metrics`.

### Tests
- Unit: Each service function with mocked DataFrames.
- Integration: Endpoint tests with testcontainers (SQL Server) or SQLite in-memory.
- Data quality: Assertions on output schema and value ranges.

---

## 5. Desktop Admin (`desktop-admin/`)

### Purpose
Administration tool for data managers: import/export CSV price data, correct erroneous prices, view job execution logs, manage users. Targets power users and demos.

### Stack
- Java 17, Java Swing, NetBeans IDE
- JDBC (SQL Server in production, SQLite for local dev/demo)
- Apache Commons CSV for import/export

### Folder Structure

```
desktop-admin/
├── src/
│   └── main/
│       └── java/
│           └── com/wattwise/admin/
│               ├── WattWiseAdmin.java           (main class)
│               ├── ui/
│               │   ├── MainFrame.java
│               │   ├── PriceTablePanel.java
│               │   ├── ImportExportPanel.java
│               │   ├── JobLogPanel.java
│               │   ├── UserManagementPanel.java
│               │   └── ConnectionDialog.java
│               ├── service/
│               │   ├── CsvService.java
│               │   ├── PriceCorrectionService.java
│               │   └── LogViewerService.java
│               ├── model/
│               │   └── PriceRecord.java
│               └── db/
│                   ├── DatabaseConnection.java
│                   └── QueryExecutor.java
├── resources/
│   ├── csv-templates/
│   │   └── price-import-template.csv
│   └── icons/
├── nbproject/                        (NetBeans project files)
└── pom.xml                           (or nbproject build config)
```

### Main Components
- **PriceTablePanel:** JTable showing price records with inline editing for correction.
- **ImportExportPanel:** File chooser for CSV import with validation and preview; export filtered data.
- **JobLogPanel:** Table view of backend job execution logs (fetched via API or DB query).
- **ConnectionDialog:** Configure SQL Server or SQLite connection at startup.

### Connection to Other Modules
- Direct JDBC to SQL Server (production) or SQLite (local).
- Can optionally call backend API for job log retrieval.
- Reads/writes the same `price_records` and `users` tables as the backend.

### Tests
- Unit: `CsvService` parse/serialize, `PriceCorrectionService` validation logic.
- Manual: UI workflow testing (import, correct, export round-trip).

---

## 6. Infrastructure & Docker (`docker/`)

### Purpose
Containerize all services and provide a one-command local development environment. Includes production-like stack with SQL Server, monitoring, and reverse proxy.

### Stack
- Docker, Docker Compose (v2), Podman Compose (compatible alternative)
- Nginx (reverse proxy + static file serving)
- Prometheus (metrics collection)
- Grafana (metrics visualization + alerting)
- SQL Server 2022 (or SQL Server for Linux)

### Folder Structure

```
docker/
├── docker-compose.yml                (full stack: backend, web, sql, analytics, prometheus, grafana)
├── docker-compose.dev.yml            (local dev overrides: hot-reload, SQLite option)
├── docker-compose.prod.yml           (production overrides: resource limits, restart policies)
├── backend/
│   └── Dockerfile                    (multi-stage: build + JRE runtime)
├── web/
│   └── Dockerfile                    (nginx:alpine serving static files)
├── analytics-python/
│   └── Dockerfile                    (python:3.11-slim)
├── nginx/
│   ├── nginx.conf                    (reverse proxy config)
│   └── default.conf                  (server blocks)
├── prometheus/
│   └── prometheus.yml                (scrape targets: backend:8080, analytics:5000)
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml
│   │   └── dashboards/
│   │       └── wattwise-overview.json
│   └── grafana.ini
└── sql-server/
    └── init-scripts/
        └── 01-create-schema.sql
```

### Service Ports (Docker Compose)

| Service | Internal Port | Mapped Port | Description |
|---|---|---|---|
| nginx | 80 | `8080` (host) | Static web + API proxy |
| backend | 8080 | — (internal) | Spring Boot API |
| sql-server | 1433 | `1433` | SQL Server database |
| analytics-python | 5000 | `5001` (host) | Python analytics |
| prometheus | 9090 | `9090` (host) | Metrics |
| grafana | 3000 | `3000` (host) | Dashboards |

### Connection to Other Modules
- Backend connects to SQL Server via JDBC (`jdbc:sqlserver://sql-server:1433`).
- Analytics connects to SQL Server via SQLAlchemy.
- Nginx proxies `/api/*` to backend, serves `web/` static files.
- Prometheus scrapes backend actuator and analytics `/metrics`.
- Grafana reads from Prometheus datasource.
- All services on a shared Docker network (`wattwise-net`).

### Tests
- Smoke test: `docker-compose up` starts all services, health checks pass.
- Prometheus targets all UP.
- Grafana dashboards render without errors.

---

## 7. Observability (Cross-cutting)

### Purpose
Provide visibility into system health, ingestion pipeline status, API performance, and business metrics (users, recommendations served, alerts triggered).

### Stack
- **Prometheus:** Metrics collection (pull-based, 15s scrape interval)
- **Grafana:** Visualization, dashboards, alerting rules
- **Spring Boot Actuator:** JVM, HTTP, JPA metrics
- **Python metrics:** `prometheus_client` library

### Key Metrics

| Metric | Type | Source | Labels |
|---|---|---|---|
| `http_requests_total` | Counter | Backend Actuator | method, endpoint, status |
| `http_request_duration_seconds` | Histogram | Backend Actuator | method, endpoint |
| `esiros_fetch_total` | Counter | PriceFetchJob | status (success/error) |
| `esiros_fetch_duration_seconds` | Histogram | PriceFetchJob | — |
| `price_records_inserted_total` | Counter | PriceFetchJob | source (esiros/manual) |
| `recommendations_served_total` | Counter | RecommendationController | appliance_type |
| `alerts_triggered_total` | Counter | AlertService | alert_type |
| `analytics_requests_total` | Counter | Python analytics | endpoint, status |
| `jvm_memory_used_bytes` | Gauge | Backend Actuator | area |
| `sql_server_connections_active` | Gauge | Backend Actuator | — |

### Grafana Dashboards
- **WattWise Overview:** System health (all services up), request rates, error rates.
- **Price Pipeline:** Ingestion status, ESIOS response times, stale data warnings.
- **Business Metrics:** Daily active users, recommendations served, alerts fired.

### Alerts (Grafana Alerting Rules)
- ESIOS fetch failure rate > 50% over 24h → Warning
- API error rate > 5% over 1h → Critical
- Backend JVM heap usage > 85% → Warning
- SQL Server connection pool exhaustion → Critical
