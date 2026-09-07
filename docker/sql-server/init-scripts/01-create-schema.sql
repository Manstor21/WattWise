-- =============================================================================
-- WattWise bootstrap schema for SQL Server.
--
-- Creates ONLY the application database, its login and its user (idempotently).
-- Tables are owned by the backend's Flyway migrations (see
-- backend/src/main/resources/db/migration), NEVER created here.
--
-- Interpolated at runtime by init.sh via sqlcmd -v using the compose
-- env vars DB_NAME / DB_USER / DB_PASSWORD.
-- =============================================================================

IF DB_ID(N'$(DB_NAME)') IS NULL
BEGIN
    PRINT 'Creating database [$(DB_NAME)].';
    CREATE DATABASE [$(DB_NAME)];
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(DB_USER)')
BEGIN
    PRINT 'Creating login [$(DB_USER)].';
    -- CHECK_POLICY = OFF keeps the default lightweight password workable in
    -- containers; use a strong DB_PASSWORD in production .env.
    CREATE LOGIN [$(DB_USER)] WITH PASSWORD = N'$(DB_PASSWORD)', CHECK_POLICY = OFF;
END
GO

USE [$(DB_NAME)];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(DB_USER)' AND type IN ('S', 'U'))
BEGIN
    PRINT 'Creating user [$(DB_USER)] in [$(DB_NAME)].';
    CREATE USER [$(DB_USER)] FOR LOGIN [$(DB_USER)];
    ALTER ROLE db_owner ADD MEMBER [$(DB_USER)];
END
GO