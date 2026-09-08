-- ---------------------------------------------------------------------------
-- WattWise — V1: initial schema (SQL Server dialect)
-- The catalog of modules describes a simplified data model. We add a "maker/type"
-- lookup table seeded by V2 to power the appliance catalog and average consumption
-- values used by the recommendation engine (avgCycleKwh, estimatedCycleMinutes).
-- ---------------------------------------------------------------------------

IF OBJECT_ID('price_records', 'U') IS NOT NULL DROP TABLE price_records;
IF OBJECT_ID('alert_preferences', 'U') IS NOT NULL DROP TABLE alert_preferences;
IF OBJECT_ID('appliances', 'U') IS NOT NULL DROP TABLE appliances;
IF OBJECT_ID('appliance_catalog', 'U') IS NOT NULL DROP TABLE appliance_catalog;
IF OBJECT_ID('users', 'U') IS NOT NULL DROP TABLE users;

-- ---------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    username      NVARCHAR(50)  NOT NULL,
    email         NVARCHAR(255) NOT NULL,
    password      NVARCHAR(255) NOT NULL,       -- BCrypt hash
    role          NVARCHAR(20)  NOT NULL DEFAULT 'USER',  -- USER | ADMIN
    created_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

-- ---------------------------------------------------------------
-- APPLIANCE CATALOG (seed data) — typical appliance types and average consumption
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
-- APPLIANCES (per-user)
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
-- PRICE_RECORDS (global price table, independent)
-- ---------------------------------------------------------------
CREATE TABLE price_records (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    timestamp         DATETIME2     NOT NULL,           -- slot start, UTC
    price_eur_per_kwh DECIMAL(12,6) NOT NULL,           -- net price
    plus_tax_eur_per_kwh DECIMAL(12,6) NULL,            -- taxes/peajes
    total_eur_per_kwh DECIMAL(12,6) NOT NULL,           -- net + tax
    source            NVARCHAR(10)  NOT NULL DEFAULT 'ESIOS',  -- ESIOS | MANUAL
    date              DATE          NOT NULL,           -- derived LocalDate (UTC)
    CONSTRAINT uq_price_records_timestamp UNIQUE (timestamp)
);

CREATE INDEX ix_price_records_date ON price_records(date);

-- ---------------------------------------------------------------
-- ALERT_PREFERENCES (one per user; appliance null = all appliances)
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
    -- NO ACTION on appliance: SQL Server forbids multiple cascade paths to the
    -- same table (users -> appliances -> alert_preferences would also reach it
    -- via users -> alert_preferences). ApplianceService.delete removes affected
    -- alert_preferences first instead.
    CONSTRAINT fk_alert_pref_appliance FOREIGN KEY (appliance_id) REFERENCES appliances(id) ON DELETE NO ACTION
);

CREATE INDEX ix_alert_pref_user ON alert_preferences(user_id);
