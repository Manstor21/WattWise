# =============================================================================
# Backend de WattWise — build en múltiples etapas.
#
# El contexto de build es la RAÍZ DEL MONOREPO (ver docker-compose.yml `build.context: ..`),
# por lo que este Dockerfile referencia rutas como ../backend/... y ../pom.xml.
# El .dockerignore opcional en docker/backend/.dockerignore recorta el contexto de build.
# =============================================================================

# ---- Etapa 1: build (Maven + Temurin 17) ------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copia todo el backend del monorepo (pom.xml primero mejora el caché de capas).
COPY backend/pom.xml ./backend/pom.xml
COPY backend/ ./backend/

RUN mvn -B -q -DskipTests -f backend/pom.xml clean package

# ---- Etapa 2: runtime (JRE 17) ----------------------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl lo usa el healthcheck de compose contra /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Crea un usuario no root para la aplicación.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Copia el fat jar ejecutable de Spring Boot desde la etapa de build.
COPY --from=build /build/backend/target/wattwise-backend-*.jar /app/app.jar

# Directorio de datos volátil usado por el perfil dev (SQLite) de override.
# Debe crearse ANTES de cambiar al usuario no root: /app pertenece a root,
# por lo que appuser no puede crear mkdir ahí. Chown para que el perfil SQLite
# pueda escribir en él.
RUN mkdir -p /app/data && chown appuser:appgroup /app/data

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]