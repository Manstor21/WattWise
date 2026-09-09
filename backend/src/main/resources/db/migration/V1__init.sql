-- ---------------------------------------------------------------------------
-- WattWise — V1: esquema inicial (dialecto SQL Server)
-- El catálogo de módulos describe un modelo de datos simplificado. Añadimos una tabla de
-- búsqueda "maker/type" poblada por V2 para alimentar el catálogo de electrodomésticos y
-- los valores de consumo medio usados por el motor de recomendaciones (avgCycleKwh,
-- estimatedCycleMinutes).
-- ---------------------------------------------------------------------------

IF OBJECT_ID('price_records', 'U') IS NOT NULL DROP TABLE price_records;
IF OBJECT_ID('alert_preferences', 'U') IS NOT NULL DROP TABLE alert_preferences;
IF OBJECT_ID('appliances', 'U') IS NOT NULL DROP TABLE appliances;
IF OBJECT_ID('appliance_catalog', 'U') IS NOT NULL DROP TABLE appliance_catalog;
IF OBJECT_ID('users', 'U') IS NOT NULL DROP TABLE users;

-- ---------------------------------------------------------------
-- USUARIOS
-- ---------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    username      NVARCHAR(50)  NOT NULL,
    email         NVARCHAR(255) NOT NULL,
    password      NVARCHAR(255) NOT NULL,       -- hash BCrypt
    role          NVARCHAR(20)  NOT NULL DEFAULT 'USER',  -- USER | ADMIN
    created_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

-- ---------------------------------------------------------------
-- CATÁLOGO DE ELECTRODOMÉSTICOS (datos de semilla) — tipos típicos y consumo medio
-- ---------------------------------------------------------------
CREATE TABLE appliance_catalog (
    id                       BIGINT IDENTITY(1,1) PRIMARY KEY,
    type                     NVARCHAR(40)  NOT NULL UNIQUE,   -- WASHING_MACHINE | DISHWASHER | EV_CHARGER | DRYER | POOL_PUMP | AC | OTHER
    avg_power_watts          INT           NOT NULL,
    avg_cycle_kwh            DECIMAL(10,3) NOT NULL,
    avg_cycle_minutes        INT           NOT NULL,
    description              NVARCHAR(255) NULL
);

-- ---------------------------------------------------------------
-- ELECTRODOMÉSTICOS (por usuario)
-- ---------------------------------------------------------------
CREATE TABLE appliances (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id               BIGINT        NOT NULL,
    name                  NVARCHAR(100) NOT NULL,
    type                  NVARCHAR(40)  NOT NULL,
    power_watts           INT           NOT NULL,
    avg_cycle_kwh         DECIMAL(10,3) NOT NULL,
    estimated_cycle_minutes INT         NOT NULL,
    is_active             BIT           NOT NULL DEFAULT 1,
    created_at            DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_appliances_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_appliances_power CHECK (power_watts > 0),
    CONSTRAINT chk_appliances_cycle CHECK (estimated_cycle_minutes > 0)
);

CREATE INDEX ix_appliances_user ON appliances(user_id);

-- ---------------------------------------------------------------
-- PRICE_RECORDS (tabla global de precios, independiente)
-- ---------------------------------------------------------------
CREATE TABLE price_records (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    timestamp         DATETIME2     NOT NULL,           -- inicio del slot, UTC
    price_eur_per_kwh DECIMAL(12,6) NOT NULL,           -- precio neto
    plus_tax_eur_per_kwh DECIMAL(12,6) NULL,            -- impuestos/peajes
    total_eur_per_kwh DECIMAL(12,6) NOT NULL,           -- neto + impuestos
    source            NVARCHAR(10)  NOT NULL DEFAULT 'ESIOS',  -- ESIOS | MANUAL
    date              DATE          NOT NULL,           -- LocalDate derivado (UTC)
    CONSTRAINT uq_price_records_timestamp UNIQUE (timestamp)
);

CREATE INDEX ix_price_records_date ON price_records(date);

-- ---------------------------------------------------------------
-- ALERT_PREFERENCES (una por usuario; appliance nulo = todos los electrodomésticos)
-- ---------------------------------------------------------------
CREATE TABLE alert_preferences (
    id                      BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id                 BIGINT  NOT NULL,
    appliance_id            BIGINT  NULL,
    threshold_pct_below_mean DECIMAL(6,2) NOT NULL DEFAULT 20.00,
    is_active               BIT     NOT NULL DEFAULT 1,
    notified_at             DATETIME2 NULL,
    created_at              DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_alert_pref_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    -- NO ACTION en appliance: SQL Server prohíbe múltiples rutas de cascade hacia la
    -- misma tabla (users -> appliances -> alert_preferences también la alcanzaría
    -- vía users -> alert_preferences). ApplianceService.delete elimina las
    -- alert_preferences afectadas primero.
    CONSTRAINT fk_alert_pref_appliance FOREIGN KEY (appliance_id) REFERENCES appliances(id) ON DELETE NO ACTION
);

CREATE INDEX ix_alert_pref_user ON alert_preferences(user_id);
