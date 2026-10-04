FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

# Usuario no root
RUN groupadd --system appgroup && useradd --system --create-home --gid appgroup appuser

# Instala dependencias del sistema necesarias para compilar algunos paquetes
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc \
    && rm -rf /var/lib/apt/lists/*

# Copia el lockfile primero para mejor caché de capas
COPY requirements.lock /app/requirements.lock
RUN pip install --no-cache-dir -r /app/requirements.lock

# Copia el código fuente
COPY . .

USER appuser

# No exponer puertos en el Dockerfile (se hace en docker-compose)
# EXPOSE 5000

CMD ["python", "-m", "flask", "--app", "app.main", "run", "--host", "0.0.0.0", "--port", "5000"]