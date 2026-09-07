# ADR-002: SQL Server for production, SQLite for dev and offline clients

**Status:** Accepted (2026-01-28)

## Context

WattWise has four places that need persistence: the Spring Boot API (users, appliances, prices, alert preferences), the Android app (which must work without connectivity), the desktop admin tool (which runs in demo mode on a laptop), and a developer booting the backend from their IDE. Each has a different tolerance for setup and a different concurrency profile. We needed one schema definition and as few SQL dialects as possible.

## Decision

SQL Server is the only server-side database (`application-prod.yml`). The Android app keeps its Room/SQLite cache for offline reads — the schema mirrors the server-side `price_records` and `appliances` tables — and SQLite is the dev/demo database (`application-dev.yml`), which the desktop-admin `ConnectionDialog` also offers as a local option. We deliberately skipped H2 for local dev: SQLite shares the same dialect surface as the Android cache, so a schema mistake caught locally is the same mistake we'd hit on a phone.

The schema never lives in JPA. We run `ddl-auto: none`; Flyway (`V1__init.sql`, `V2__seed_catalog.sql`) is the single source of truth, written in SQL Server dialect and applied by both Spring Boot and the container init scripts. The main dialect gaps we hit are `IDENTITY` vs. SQLite's `AUTOINCREMENT` and `DATETIME2` vs. SQLite timestamps — both mostly hidden behind Hibernate/JPQL and the few raw SQL scripts.

## Consequences

Positive: one schema definition to maintain; zero-setup local dev and demo mode; a realistic offline target for Android from day one. The desktop admin reads/writes the same `users` and `price_records` tables whether pointed at SQL Server or a local SQLite file.

Negative: two SQL dialects to keep in mind in raw scripts; SQLite lacks stored procedures and advanced indexing (acceptable for a read-mostly cache); the Room cache and SQL Server stay in sync through the last-write-wins protocol in `ConflictResolver`, with server-assigned timestamps overriding client timestamps on conflict.