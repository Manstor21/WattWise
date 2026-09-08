# =============================================================================
# Microservicio de analítica de WattWise — python:3.11-slim (bookworm).
#
# El contexto de build es la RAÍZ DEL MONOREPO (docker-compose.yml `build.context: ..`).
# Instala el driver ODBC de Microsoft para que la DATABASE_URL de producción
# (mssql+pyodbc) pueda hablar con SQL Server. pyodbc es una dependencia solo de
# contenedor y queda fuera de los requisitos de desarrollo de analytics-python.
# =============================================================================

FROM python:3.11-slim-bookworm

# El repositorio de Microsoft requiere claves apt + curl/gnupg.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gnupg apt-transport-https ca-certificates \
    && curl -fsSL https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/microsoft-prod.gpg] https://packages.microsoft.com/debian/12/prod bookworm main" \
        > /etc/apt/sources.list.d/mssql-release.list \
    && apt-get update \
    && ACCEPT_EULA=Y apt-get install -y --no-install-recommends msodbcsql18 \
    && apt-get purge -y --auto-remove gnupg \
    && rm -rf /var/lib/apt/lists/*

# Usuario no root. --create-home también para usuarios --system: el servidor de
# control de gunicorn escribe su socket bajo $HOME y falla con EACCES cuando el
# directorio home no existe.
RUN groupadd --system appgroup && useradd --system --create-home --gid appgroup appuser

WORKDIR /app

# Instala primero las dependencias de Python (mejor caché de capas).
COPY analytics-python/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# gunicorn + pyodbc son dependencias de runtime solo de contenedor.
RUN pip install --no-cache-dir gunicorn pyodbc

# Copia el código fuente de la aplicación.
COPY analytics-python/app /app/app

USER appuser
EXPOSE 5000

# La app factory lee la configuración del entorno (DATABASE_URL, PORT, ...).
ENV FLASK_ENV=production
ENV PORT=5000

CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "2", "--threads", "2", "app.main:create_app()"]