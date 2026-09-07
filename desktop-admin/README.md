# WattWise Desktop Admin

Administration tool for **WattWise** data managers. It connects **directly to the database via
JDBC** (bypassing the REST API) to import/export CSV price data, correct erroneous prices, view
job execution logs and manage users.

Built with **Java 17 + Java Swing** (no JavaFX), **Apache Commons CSV**, **JUnit 5** and the JDBC
drivers for SQL Server (production) and SQLite (local demo).

---

## Stack & layout

```
desktop-admin/
├── src/main/java/com/wattwise/admin/
│   ├── WattWiseAdmin.java            main: system L&F, opens ConnectionDialog → MainFrame
│   ├── model/PriceRecord.java         price_records row + local colour override
│   ├── db/DatabaseConnection.java     single JDBC connection (SQLite/SQL Server), demo init
│   ├── db/QueryExecutor.java          PreparedStatement queries → POJOs (zero SQL injection)
│   ├── service/CsvService.java        CSV parse/serialize + validation (Apache Commons CSV)
│   ├── service/PriceCorrectionService.java  correction validation + UPDATE
│   ├── service/LogViewerService.java  job log + price_records summary
│   └── ui/                            Swing panels (+ TrafficLightIcon, ConnectionDialog)
├── src/main/resources/
│   ├── csv-templates/price-import-template.csv
│   └── db/demo-init.sql               SQLite demo schema (mirrors backend V1__init.sql)
└── src/test/java/                     JUnit 5 tests (pure logic, no Swing)
```

Architecture follows the module contract in `docs/architecture/modules.md`: clean layering
`model → service → db → ui`, all UI strings in **Spanish**, code comments and docs in **English**.

---

## Run it

Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn clean package          # runs the 26 unit tests, builds the fat jar
java -jar target/desktop-admin-1.0.0.jar
```

The jar is built with `maven-shade-plugin` (Main-Class `com.wattwise.admin.WattWiseAdmin`) and
bundles SQLite, Commons CSV and the SQL Server driver, so it runs standalone: `java -jar`.

### Opening in NetBeans

NetBeans imports Maven projects natively: **File → Open Project…** and select this folder.
NetBeans reads `pom.xml` and wires the classpath automatically; no `nbproject/` files are needed.
The run goal is simply `package` / `exec:java` (or right-click the project → *Run*, which runs
`com.wattwise.admin.WattWiseAdmin`). IntelliJ IDEA and VS Code (with the Java extensions) work
too.

---

## Connection modes (ConnectionDialog)

| Mode | Purpose | Notes |
|------|---------|-------|
| **SQLite (demo local)** | Quick local demo, zero setup | Default file `wattwise-admin-demo.db` in the working directory. A missing file is created with `db/demo-init.sql` plus **deterministic** demo data: 3 users, ~2 days of synthetic hourly prices (48 slots), a small job log. |
| **SQL Server (production)** | Real database | JDBC URL + user + password. Example: `jdbc:sqlserver://localhost:1433;databaseName=wattwise;encrypt=true;trustServerCertificate=true`. |

- "Probar conexión" runs `SELECT 1`.
- The last mode is saved to `~/.wattwise-admin.properties`
  (`java.util.Properties`). **The password is never persisted** — it stays in memory for the
  lifetime of the process; reconnecting requires typing it again.
- All LONG queries run on a `SwingWorker` behind an indeterminate progress bar (status bar) so
  the UI never freezes.

---

## Database contract

The tools read/write the exact tables of the backend
(`backend/src/main/resources/db/migration/V1__init.sql`):

### `price_records`
`id`, `timestamp` (UNIQUE, DATETIME2 / ISO-8601 UTC), `price_eur_per_kwh`,
`plus_tax_eur_per_kwh` (nullable), `total_eur_per_kwh`, `source` (`ESIOS|MANUAL`),
`date` (derived from `timestamp`).

Timestamps travel between Java and JDBC as fixed-width UTC ISO-8601 strings
(`2025-06-16T08:00:00Z`) so comparisons are uniform across SQLite (TEXT) and SQL Server
(DATETIME2).

### Admin-only tables (SQLite demo only)
The backend has **no** `job_log` nor a colour column and **no** activation flag on `users`
(it only defines `role USER|ADMIN`). To keep the backend schema untouched the demo database
adds:

| Table / column | Purpose | SQL Server |
|---|---|---|
| `job_log (id, job_name, status, started_at, finished_at, message, rows_affected)` | job execution log | requires **manual creation** (see snippet below); panel falls back to a `price_records` summary otherwise |
| `price_color_override (price_record_id, color, updated_at)` | admin colour overrides (GREEN/AMBER/RED) | optional; the tool detects it via JDBC metadata and stays read-only without it |
| `users.is_active` | demo user toggle | the real schema has no such column, so the Users panel is **read-only** there |

```sql
-- Optional SQL Server manual setup (job log)
CREATE TABLE job_log (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    job_name      NVARCHAR(100) NOT NULL,
    status        NVARCHAR(10)  NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    started_at    DATETIME2     NULL,
    finished_at   DATETIME2     NULL,
    message       NVARCHAR(255) NULL,
    rows_affected INT           NOT NULL DEFAULT 0
);

-- Optional SQL Server manual setup (colour overrides)
CREATE TABLE price_color_override (
    price_record_id BIGINT PRIMARY KEY REFERENCES price_records(id),
    color        NVARCHAR(10) NOT NULL CHECK (color IN ('GREEN','AMBER','RED')),
    updated_at   DATETIME2    NULL
);
```

### Upsert strategy
CSV import key is **`price_records.timestamp`** (UNIQUE). A row whose timestamp already exists
is an **UPDATE**, otherwise an **INSERT**. SQLite uses `INSERT … ON CONFLICT(timestamp) DO
UPDATE`; SQL Server uses `MERGE`. Colour rows are written with an UPDATE-then-INSERT into
`price_color_override` (empty colour **clears** the override so the backend traffic-light
classifier applies again).

### Security
Every query uses `PreparedStatement` parameters — no string-concatenated SQL anywhere, no
injection surface. No credentials are hard-coded; production values come from the dialog and
are kept in memory only.

---

## CSV format

Header (template: `csv-templates/price-import-template.csv`):

```
timestamp,price_eur_per_kwh,plus_tax_eur_per_kwh,total_eur_per_kwh,source,color
```

| Column | Rules |
|---|---|
| `timestamp` | ISO-8601 with offset (e.g. `2025-06-16T08:00:00Z`). Upsert key. |
| `price_eur_per_kwh` | decimal ≥ 0 (comma decimals accepted when quoted, e.g. `"0,098200"`). |
| `plus_tax_eur_per_kwh` | decimal ≥ 0, may be empty. |
| `total_eur_per_kwh` | decimal ≥ 0. A mismatch against `price + plus_tax` is a non-blocking **warning**. |
| `source` | `ESIOS` or `MANUAL`. |
| `color` | optional `GREEN|AMBER|RED`; empty = no override. |

- **Import**: file → validated preview table (valid rows green, invalid red, per-row error list) →
  "Importar válidas" upserts and reports *insertadas / actualizadas / omitidas*.
- **Export**: optional date range filter; output is **RFC 4180** CSV written as **UTF-8 with BOM**
  so Microsoft Excel (Spanish locale) opens it correctly. The exported header matches the import
  template, so files round-trip.
- `id` and `date` are deliberately not part of the file: `id` is auto-generated and `date` is
  derived from `timestamp`.

`price-import-template.csv` also contains sample rows — it is not processed by the application
itself, but you can import it as a first smoke test.

---

## Panels

- **Precios** — sortable `JTable` of `price_records` with the traffic-light colour; inline
  editing of price / tax / total / colour; date-range filter; "Guardar cambios" validates and
  executes `UPDATE … WHERE id = ?` (reports the number of updated rows). Total mismatch is warned,
  not blocked.
- **Importar / Exportar** — CSV preview + validation + upsert; filtered export (UTF-8 BOM).
- **Jobs** — `job_log` table when present; otherwise a pipeline summary derived from
  `price_records` (record count, last timestamp, average total, counts by source) with the note
  *"Log de jobs no disponible — consulta directa a PRICE_RECORD"*.
- **Usuarios** — list of `users` (never passwords). Toggle enabled only when the schema exposes
  `is_active` (SQLite demo); on the real backend schema it is a read-only view with an
  explanation.

---

## Tests

```bash
mvn test
```

Pure JVM tests (no Swing instantiated):

- `CsvServiceTest` — parsing, validation errors (bad header, negative price, bad ISO timestamp,
  bad source/colour), comma decimals, BOM stripping, serialize→parse round-trip.
- `PriceCorrectionServiceTest` — validation rules (negative values, invalid colours, tolerance
  warning on total mismatch).
- `QueryExecutorTest` — against a real embedded SQLite database
  (`jdbc:sqlite::memory:` on a single connection, full demo schema+seed): upsert insert/update,
  price correction + colour override, range filters, user toggle, deterministic seed, and a
  temp-file demo database bootstrapped with `DatabaseConnection.connectSqlite`.

## Known notes

- SQL Server connections are runtime-dependency only: the app starts fine without a server, and
  the dialog's test button reports the real driver error.
- `total` mismatch rules are deliberately permissive (informational), matching data ingested from
  external sources where rounding differs between providers.
- The demo is a *portfolio* implementation: the SQL Server `MERGE` path is included but not
  covered by automated tests (requires a live SQL Server instance).