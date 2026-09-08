-- =============================================================================
-- Esquema de arranque de WattWise para SQL Server.
--
-- Crea SOLO la base de datos de la aplicación, su login y su usuario (de forma idempotente).
-- Las tablas son propiedad de las migraciones de Flyway del backend (ver
-- backend/src/main/resources/db/migration), NUNCA se crean aquí.
--
-- Se interpola en tiempo de ejecución por init.sh vía sqlcmd -v usando las
-- variables de entorno DB_NAME / DB_USER / DB_PASSWORD de compose.
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
    -- CHECK_POLICY = OFF mantiene utilizable la contraseña ligera por defecto en
    -- contenedores; usa una DB_PASSWORD fuerte en el .env de producción.
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