-- ---------------------------------------------------------------------------
-- WattWise Desktop Admin — esquema de demostración para SQLite
-- Espejo de backend/src/main/resources/db/migration/V1__init.sql traducido a
-- la sintaxis de SQLite, más las tablas de demostración exclusivas del administrador.
--
-- NOTA SOBRE LAS DIFERENCIAS RESPECTO AL BACKEND:
--   * price_color_override  — overrides de color de semáforo solo para el admin.
--     NO forma parte del esquema del backend; se trata de una comodidad local.
--   * job_log               — log de ejecución de jobs. El backend no tiene esa
--     tabla; esta copia de demostración solo existe en SQLite. En SQL Server se
--     requiere creación manual (ver README).
--   * users.is_active       — indicador de activación para el botón de activar/
--     desactivar de la demo. El esquema real del backend solo define role
--     USER|ADMIN; por eso en SQL Server los usuarios se muestran de solo lectura.
-- Una sentencia por línea; DatabaseConnection.executeScript ejecuta este archivo.
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