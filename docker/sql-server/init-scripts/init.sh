#!/bin/bash
# =============================================================================
# WattWise SQL Server bootstrap wrapper.
#
# The official mcr.microsoft.com/mssql/server image does not run an
# initdb.d-style folder, so this entrypoint:
#   1. starts sqlservr in the background,
#   2. waits until it accepts connections,
#   3. applies every /opt/wattwise/init/*.sql script (mounted read-only from
#      docker/sql-server/init-scripts/) — idempotent by design,
#   4. waits on sqlservr so PID 1 lifecycle keeps working.
#
# Environment: MSSQL_SA_PASSWORD, DB_NAME, DB_USER, DB_PASSWORD
# are injected by docker-compose.yml from the .env file.
# =============================================================================

set -eu

SQLCMD=/opt/mssql-tools18/bin/sqlcmd

log() { echo "[wattwise-init] $*"; }

# The compose file overrides the image ENTRYPOINT to this script, so we are
# PID 1: start SQL Server as a child and never exit until it does.
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

# Apply init SQL (variables interpolated from the compose environment).
for f in /opt/wattwise/init/*.sql; do
    [ -e "$f" ] || continue
    log "Applying $f"
    "$SQLCMD" -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -d master -b \
        -v DB_NAME="$DB_NAME" DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" -i "$f"
done
log "Init scripts applied."

# Stay in the foreground; propagate sqlservr exit status.
wait "$SQLSERVR_PID"