# =============================================================================
# WattWise backend — multi-stage build.
#
# Build context is the MONOREPO ROOT (see docker-compose.yml `build.context: ..`),
# so this Dockerfile references paths as ../backend/... and ../pom.xml as needed.
# The optional .dockerignore at docker/backend/.dockerignore prunes build context.
# =============================================================================

# ---- Stage 1: build (Maven + Temurin 17) ------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy the whole monorepo backend (pom.xml first improves layer caching).
COPY backend/pom.xml ./backend/pom.xml
COPY backend/ ./backend/

RUN mvn -B -q -DskipTests -f backend/pom.xml clean package

# ---- Stage 2: runtime (JRE 17) ----------------------------------------------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl is used by the compose healthcheck against /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user for the application.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Copy the executable Spring Boot fat jar from the build stage.
COPY --from=build /build/backend/target/wattwise-backend-*.jar /app/app.jar

USER appuser
EXPOSE 8080

# Volatile data dir used by the dev (SQLite) profile override.
RUN mkdir -p /app/data

ENTRYPOINT ["java", "-jar", "/app/app.jar"]