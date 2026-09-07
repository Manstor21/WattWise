-- ---------------------------------------------------------------------------
-- WattWise Desktop Admin — SQLite demo schema
-- Mirror of backend/src/main/resources/db/migration/V1__init.sql translated to
-- the SQLite dialect, plus the admin-only demo tables.
--
-- NOTE ON DIFFERENCES VS THE BACKEND:
--   * price_color_override  — admin-only traffic-light colour overrides. NOT part
--     of the backend schema; local convenience.
--   * job_log               — job execution log. The backend has no such table;
--     this demo copy exists only in SQLite. SQL Server requires manual creation
--     (see README).
--   * users.is_active       — activation flag for the demo toggle button. The real
--     backend schema only defines role USER|ADMIN; SQL Server therefore shows
--     users read-only.
-- One statement per line; this file is executed by DatabaseConnection.executeScript.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS price_records (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp         TEXT NOT NULL UNIQUE,
    price_eur_per_kwh REAL NOT NULL,
    plus_tax_eur_per_kwh REAL,
    total_eur_per_kwh REAL NOT NULL,
    source            TEXT NOT NULL DEFAULT 'ESIOS',
    date              TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_price_records_date ON price_records(date);
CREATE TABLE IF NOT EXISTS price_color_override (
    price_record_id   INTEGER PRIMARY KEY,
    color             TEXT NOT NULL CHECK (color IN ('GREEN','AMBER','RED')),
    updated_at        TEXT,
    FOREIGN KEY (price_record_id) REFERENCES price_records(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS users (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username   TEXT NOT NULL UNIQUE,
    email      TEXT NOT NULL UNIQUE,
    password   TEXT NOT NULL,
    role       TEXT NOT NULL DEFAULT 'USER',
    is_active  INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS job_log (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    job_name      TEXT NOT NULL,
    status        TEXT NOT NULL CHECK (status IN ('SUCCESS','FAILED')),
    started_at    TEXT,
    finished_at   TEXT,
    message       TEXT,
    rows_affected INTEGER NOT NULL DEFAULT 0
);