# WattWise — System Architecture

## Executive Summary

WattWise addresses the growing complexity of hourly electricity pricing in Spain's PVPC regulated market, where real-time prices now update every 15 minutes (up to 96 values per day) and can vary by over 400% between the cheapest and most expensive hours. The system ingests pricing data from Red Eléctrica de España's public ESIOS API, processes it through a Spring Boot backend, and delivers actionable recommendations — telling users when to run appliances, charge EVs, or shift loads — via a web dashboard, Android app, and desktop administration tool. A separate Python microservice provides statistical analytics. The architecture prioritizes reliability against an external API with no SLA, offline resilience on mobile, and operational simplicity through containerized deployment.

---

## High-Level Architecture Diagram

```mermaid
flowchart TB
    subgraph Clients["Client Layer"]
        WEB["🌐 Web Dashboard<br/>HTML/CSS/JS + jQuery<br/>:80 via nginx"]
        ANDROID["📱 Android App<br/>Java · WorkManager<br/>SQLite offline cache"]
        DESKTOP["🖥️ Desktop Admin<br/>Java Swing · NetBeans<br/>SQL Server / SQLite local"]
    end

    subgraph Backend["Backend — Spring Boot"]
        API["REST API<br/>JWT Auth · Rate Limiting<br/>:8080"]
        JOB["Scheduled Job<br/>Price fetcher<br/>~20:15 daily + every 2h"]
        STRATEGY["Recommendation Engine<br/>Strategy Pattern<br/>per appliance type"]
        ALERTS["Alert Service<br/>Observer Pattern<br/>threshold notifications"]
    end

    subgraph Data["Persistence"]
        SQLS["SQL Server<br/>Production DB<br/>:1433"]
        SQLITE_L["SQLite<br/>Local / Demo DB"]
        SQLITE_A["SQLite<br/>Android offline cache"]
    end

    subgraph Analytics["Analytics Microservice"]
        FLASK["Python Flask / FastAPI<br/>Pandas · :5000"]
    end

    subgraph External["External APIs"]
        ESIOS["ESIOS — Red Eléctrica<br/>PVPC 15-min prices<br/>apidatos.ree.es"]
    end

    subgraph Infra["Infrastructure & Observability"]
        NGINX["Nginx<br/>Reverse proxy + static files<br/>:80"]
        PROM["Prometheus<br/>Metrics scrape<br/>:9090"]
        GRAF["Grafana<br/>Dashboards<br/>:3000"]
        DOCKER["Docker Compose<br/>/ Podman Compose"]
    end

    %% Client → Backend
    WEB -->|"HTTP/JSON"| NGINX
    NGINX -->|"proxy_pass"| API
    ANDROID -->|"HTTPS + JWT"| API
    DESKTOP -->|"HTTP/JSON"| SQLS

    %% Backend → Data
    API -->|"JPA/Hibernate"| SQLS
    API -->|"JDBC (local/dev)"| SQLITE_L
    JOB -->|"read/write"| SQLS
    STRATEGY -->|"read"| SQLS
    ALERTS -->|"read/write"| SQLS

    %% Backend → External
    JOB -->|"REST GET<br/>retry + backoff"| ESIOS

    %% Analytics
    FLASK -->|"SQLAlchemy"| SQLS
    API -->|"REST (internal)"| FLASK

    %% Android offline
    ANDROID -.->|"offline mode"| SQLITE_A
    ANDROID -->|"sync on reconnect"| API

    %% Observability
    API -->|"actuator + micrometer"| PROM
    JOB -->|"metrics"| PROM
    FLASK -->|"/metrics"| PROM
    PROM --> GRAF

    %% Infra
    DOCKER -.->|"orchestrates"| API
    DOCKER -.->|"orchestrates"| SQLS
    DOCKER -.->|"orchestrates"| FLASK
    DOCKER -.->|"orchestrates"| PROM
    DOCKER -.->|"orchestrates"| GRAF
    DOCKER -.->|"orchestrates"| NGINX

    classDef clientStyle fill:#4A90D9,stroke:#2C5F8A,color:#fff
    classDef backendStyle fill:#27AE60,stroke:#1E8449,color:#fff
    classDef dataStyle fill:#E67E22,stroke:#D35400,color:#fff
    classDef analyticsStyle fill:#8E44AD,stroke:#6C3483,color:#fff
    classDef externalStyle fill:#E74C3C,stroke:#C0392B,color:#fff
    classDef infraStyle fill:#7F8C8D,stroke:#5D6D7E,color:#fff

    class WEB,ANDROID,DESKTOP clientStyle
    class API,JOB,STRATEGY,ALERTS backendStyle
    class SQLS,SQLITE_L,SQLITE_A dataStyle
    class FLASK analyticsStyle
    class ESIOS externalStyle
    class NGINX,PROM,GRAF,DOCKER infraStyle
```

---

## Module Overview

| Module | Responsibility | Technology | Port | Input | Output |
|---|---|---|---|---|---|
| **backend** | REST API, business logic, scheduling, auth | Spring Boot 3.x, Java 17, Maven | `:8080` | HTTP JSON requests, ESIOS API responses | JSON API responses, DB writes, alert triggers |
| **web** | User-facing dashboard, traffic-light UI | HTML5, CSS3, vanilla JS, jQuery | `:80` (via nginx) | AJAX calls to backend API | Rendered dashboard, appliance schedules |
| **android** | Mobile client, offline mode, push-style local notifications | Android SDK (Java), WorkManager, Room/SQLite | — | API responses, local SQLite | UI, local notifications, cached data |
| **analytics-python** | Statistical analytics, price trends, savings estimates | Flask/FastAPI, Pandas, SQLAlchemy | `:5000` | SQL Server data, backend internal calls | JSON analytics endpoints |
| **desktop-admin** | Data admin: CSV import/export, price correction, job logs | Java Swing, NetBeans, JDBC | — | SQL Server direct + API | Admin UI, modified DB records |
| **infra** | Containerization, orchestration, observability | Docker, Docker Compose, Nginx, Prometheus, Grafana | `:80/:3000/:9090` | Config files, metrics | Running services, dashboards, alerts |

---

## Principal Data Flows

### 1. Daily Price Ingestion (Scheduled)

```mermaid
sequenceDiagram
    participant Cron as Job Scheduler
    participant Backend as Spring Boot
    participant ESIOS as ESIOS API (REE)
    participant DB as SQL Server

    Note over Cron: Triggers at ~20:15 UTC+1 daily<br/>(D-1 prices published)<br/>+ retry at 20:30, 21:00, 22:00

    Cron->>Backend: execute()
    Backend->>ESIOS: GET /datos/mercados/precios-mercados-tiempo-real
    alt ESIOS responds 200
        ESIOS-->>Backend: JSON with 96 price records
        Backend->>Backend: Validate, normalize, detect gaps
        Backend->>DB: UPSERT PriceRecords (up to 96 rows)
        Backend->>Backend: Recalculate traffic-light classification
        Backend->>Backend: Trigger AlertService.checkThresholds()
    else ESIOS timeout / 5xx
        Backend->>Backend: Exponential backoff (2s, 4s, 8s, max 3 retries)
        alt All retries exhausted
            Backend->>DB: Log failed fetch, keep last known prices
            Backend->>Backend: Fire alert: "ESIOS data unavailable"
        end
    end
```

### 2. User Consultation & Recommendation

```mermaid
sequenceDiagram
    participant User as User (Web/Android)
    participant API as REST API
    participant Engine as Recommendation Engine
    participant DB as SQL Server
    participant Analytics as Python Analytics

    User->>API: GET /api/recommendations?userId=X
    API->>API: Validate JWT token
    API->>DB: Fetch user appliances
    API->>DB: Fetch today's + tomorrow's PriceRecords
    API->>Engine: compute(appliances, prices)
    loop For each appliance
        Engine->>Engine: Strategy by type<br/>(washer, dishwasher, EV, etc.)
        Engine->>Engine: Apply traffic-light classification<br/>(green/amber/red per slot)
        Engine->>Engine: Find optimal window(s)
    end
    Engine-->>API: Ranked recommendations
    API-->>User: JSON response
    opt Analytics enrichment
        API->>Analytics: GET /api/savings-estimate?userId=X
        Analytics-->>API: Savings projection, trend data
    end
```

### 3. Android Offline ↔ Sync

```mermaid
sequenceDiagram
    participant App as Android App
    participant Local as SQLite (Room)
    participant API as REST API

    alt Online
        App->>API: GET /api/prices/today
        API-->>App: JSON prices
        App->>Local: Upsert PriceRecords
        App->>API: GET /api/recommendations
        API-->>App: JSON recommendations
        App->>Local: Cache recommendations
    else Offline
        App->>Local: Query cached prices
        Local-->>App: Last known prices + recommendations
        App->>App: Show traffic-light from cache<br/>+ "offline" indicator
    end

    Note over App: WorkManager periodic task (6h)<br/>tries sync when connectivity returns
    App->>API: Sync pending appliance changes
    API-->>App: Confirm sync
```

---

## Key Architectural Decisions & Trade-offs

### A. Separate Python Microservice for Analytics

**Decision:** Analytics live in a standalone Python Flask/FastAPI service, not inside the Spring Boot backend.

**Why:**
- **Library fit:** Pandas is the de-facto standard for time-series analysis, rolling windows, and statistical aggregation. Replicating this in Java would require引入ing Commons Math or similar with less community support.
- **Independent scaling:** Analytics queries (e.g., "average price by weekday over 6 months") are CPU-bound and can be scaled independently without restarting the main API.
- **Team flexibility:** A Python service lowers the barrier for data-science contributions without requiring Java expertise.

**Trade-offs accepted:**
- Network hop between backend and analytics (mitigated: both on same Docker network, internal REST call, <1ms latency).
- Two runtimes to maintain (mitigated: Docker encapsulates both).
- Data consistency requires SQL Server as single source of truth (both services read from it).

### B. SQL Server (Production) vs SQLite (Local/Offline)

**Decision:** SQL Server for all server-side persistence; SQLite for local dev/demo, desktop-admin local mode, and Android offline cache.

**Why:**
- **SQL Server** provides transactional integrity, concurrent access, full-text search, and aligns with enterprise deployment targets.
- **SQLite** requires zero setup for local development and demo, and is the only viable embedded DB on Android.
- **Synchronization** between Android SQLite and SQL Server is handled via a last-write-wins sync protocol over the REST API when connectivity is restored. The backend assigns server-side timestamps that override client timestamps on conflict.

**Trade-offs accepted:**
- SQL dialect differences (HQL/JPQL abstracts most; raw SQL scripts use dialect-specific syntax with profiles).
- SQLite lacks stored procedures and advanced indexing (acceptable for offline read-mostly cache).

### C. External API Resilience (ESIOS / REE)

**Decision:** Defensive ingestion with retries, fallbacks, and graceful degradation.

**Implementation:**
- **Retry with exponential backoff:** 3 attempts, base delay 2s, max delay 30s. Circuit breaker pattern after 5 consecutive failures.
- **Default values:** If ESIOS is unreachable after all retries, the system retains the last successfully fetched prices and marks them as `STALE`. The UI displays a "prices may be outdated" banner.
- **Late publication handling:** If prices for D+1 are not available by 22:00, the scheduler fires an alert and retries at 23:00 and 00:00.
- **Monitoring:** Prometheus counter `esiros_fetch_total{status}` tracks success/failure rates. Grafana alerts fire if failure rate > 50% over 24h.

**Trade-offs accepted:**
- No real-time price streaming (ESIOS does not offer WebSocket/SSE). Polling every 2h is sufficient for day-ahead market.
- Stale data risk is bounded by the retry schedule and made visible to users.

### D. Monorepo

**Decision:** All modules in a single Git repository with clear directory boundaries.

**Why:**
- **Atomic cross-module changes:** A backend API contract change and the corresponding web/Android consumer update can be committed together.
- **Simplified dependency management:** One CI/CD pipeline, one Docker Compose file, shared scripts.
- **Developer experience:** Single clone to get the full system; no need to coordinate across repositories.

**Trade-offs accepted:**
- Repository size grows over time (mitigated: `.gitignore` for build artifacts, LFS for large assets if needed).
- CI builds may be slower for unrelated changes (mitigated: path-based triggers in GitHub Actions).

### E. Local Notifications via WorkManager (Not FCM)

**Decision:** Android notifications are delivered locally via WorkManager + NotificationCompat. Firebase Cloud Messaging is documented as a future extension but not implemented in this phase.

**Why:**
- **No server dependency:** Local notifications work offline, aligning with the offline-first architecture.
- **No Firebase project setup:** Eliminates Google Cloud Console configuration, `google-services.json` management, and FCM token lifecycle.
- **Sufficient for use case:** The user needs a reminder at the optimal window — this is a local scheduling decision, not a server-pushed event.
- **Privacy:** No device tokens leave the device.

**Trade-offs accepted:**
- Cannot notify user of sudden price changes outside the pre-computed window (mitigated: WorkManager re-checks every 6h).
- No cross-device notification sync (acceptable for single-device personal use).

---

## Non-Functional Requirements

| Category | Requirement | Target |
|---|---|---|
| **Performance** | API response time (p95) | < 200ms for price queries, < 500ms for recommendations |
| **Performance** | Price ingestion latency (ESIOS → DB) | < 10s for full day (96 records) |
| **Availability** | System uptime | 99.5% (single-server deployment) |
| **Availability** | External API failure handling | Graceful degradation with stale data, max 5min user-visible delay |
| **Security** | Authentication | JWT with 24h expiry, refresh token rotation |
| **Security** | Transport | HTTPS enforced in production (Let's Encrypt / self-signed for local) |
| **Security** | Secrets management | Environment variables, Docker secrets — no hardcoded credentials |
| **Testing** | Backend unit test coverage | ≥ 70% (JaCoCo) |
| **Testing** | Integration tests | API contract tests for all REST endpoints |
| **Testing** | Android | Unit tests for ViewModels, instrumentation tests for Room DAOs |
| **Observability** | Metrics | Prometheus scrape for backend, analytics, and job scheduler |
| **Observability** | Dashboards | Grafana: price ingestion status, API latency, error rates, job runs |
| **Observability** | Logging | Structured JSON logs, retained 30 days, rotatable |
| **Data** | Price retention | Minimum 2 years of historical PVPC data |
| **Data** | Backup | Daily automated backup of SQL Server (script in `scripts/`) |
| **Compliance** | Data minimization | Only store user email, hashed password, appliance metadata — no payment data |
| **Compliance** | API usage | Respect REE API rate limits and terms of use |
