# =============================================================================
# WattWise analytics microservice — python:3.11-slim (bookworm).
#
# Build context is the MONOREPO ROOT (docker-compose.yml `build.context: ..`).
# Installs the Microsoft ODBC driver so the production DATABASE_URL
# (mssql+pyodbc) can talk to SQL Server. pyodbc is a container-only dep and
# stays out of analytics-python's dev requirements.
# =============================================================================

FROM python:3.11-slim-bookworm

# Microsoft's repo requires apt keys + curl/gnupg.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg apt-transport-https ca-certificates \
    && curl -fsSL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/microsoft-prod.gpg] https://packages.microsoft.com/debian/12/prod bookworm main" \
        > /etc/apt/sources.list.d/mssql-release.list \
    && apt-get update \
    && ACCEPT_EULA=Y apt-get install -y --no-install-recommends msodbcsql18 \
    && apt-get purge -y --auto-remove gnupg \
    && rm -rf /var/lib/apt/lists/*

# Non-root user
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# Install Python dependencies first (better layer caching).
COPY analytics-python/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# gunicorn + pyodbc are container-only runtime dependencies.
RUN pip install --no-cache-dir gunicorn pyodbc

# Copy the application source.
COPY analytics-python/app /app/app

USER appuser
EXPOSE 5000

# The app factory reads config from env (DATABASE_URL, PORT, ...).
ENV FLASK_ENV=production
ENV PORT=5000

CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "2", "--threads", "2", "app.main:create_app()"]