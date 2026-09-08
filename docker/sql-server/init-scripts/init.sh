#!/bin/bash
# =============================================================================
# Wrapper de arranque de SQL Server para WattWise.
#
# La imagen oficial mcr.microsoft.com/mssql/server no ejecuta una carpeta
# estilo initdb.d, por lo que este entrypoint:
#   1. arranca sqlservr en segundo plano,
#   2. espera hasta que acepte conexiones,
#   3. aplica cada script /opt/wattwise/init/*.sql (montado de solo lectura desde
#      docker/sql-server/init-scripts/) — idempotente por diseño,
#   4. espera a sqlservr para que el ciclo de vida del PID 1 siga funcionando.
#
# Entorno: MSSQL_SA_PASSWORD, DB_NAME, DB_USER, DB_PASSWORD
# son inyectados por docker-compose.yml desde el archivo .env.
# =============================================================================

set -eu

SQLCMD=/opt/mssql-tools18/bin/sqlcmd

log() { echo "[wattwise-init] $*"; }

# El archivo compose sobreescribe el ENTRYPOINT de la imagen con este script, por
# lo que somos PID 1: arrancamos SQL Server como hijo y no salimos hasta que él salga.
/opt/mssql/bin/sqlservr &
SQLSERVR_PID=$!

log "Waiting for SQL Server to accept connections..."
ready=0
for i in $(seq 1 60); do
    if "$SQLCMD" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d master -Q "SELECT 1" -b -o /dev/null 2>/dev/null; then
        ready=1
        break
    fi
    sleep 2
    if [ "$i" -eq 60 ]; then
        log "SQL Server did not become ready within 120s"
        exit 1
    fi
done
[ "$ready" -eq 1 ] && log "SQL Server ready."

# Aplica el SQL de init (variables interpoladas desde el entorno de compose).
for f in /opt/wattwise/init/*.sql; do
    [ -e "$f" ] || continue
    log "Applying $f"
    "$SQLCMD" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d master -b \
        -v DB_NAME="$DB_NAME" DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" -i "$f"
done
log "Init scripts applied."

# Quédate en primer plano; propaga el estado de salida de sqlservr.
wait "$SQLSERVR_PID"