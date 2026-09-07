# ADR-004: One repository, not six

**Status:** Accepted (2026-02-19)

## Context

WattWise is really six projects glued together: the Spring Boot backend, the static web client, the Android app, the Python analytics service, the Swing desktop tool, and the Docker/observability stack. When an API contract changes — say `PriceController` starts returning traffic-light colors as part of each `price_records` payload — the web dashboard, the Android `PriceDao`, and the desktop admin all have to change in the same release. Split across repositories, that's three PRs, three approvals, and three deploy windows for one logical change. We chose to keep everything in a single Git repository instead.

## Decision

All modules live in one repo with clear directory boundaries (`backend/`, `web/`, `android/`, `analytics-python/`, `desktop-admin/`, `docker/`), one CI pipeline (GitHub Actions with path-based triggers), and a single `docker-compose.yml` that brings up the whole system. A schema change in Flyway, the matching JPA entity, and the desktop admin's `QueryExecutor` can land in the same atomic commit.

## Consequences

Positive: atomic cross-module changes; a single clone gives you the full system; one release branch and one compose file to reason about. Shared Flyway migrations and shared API contracts can't silently drift between modules, which was the biggest day-to-day pain we were avoiding.

Negative: the checkout grows heavy and the working tree is bigger than it needs to be for any single contributor (mitigated with `.gitignore` and keeping binaries/artifacts out of the tree); a full CI run is slower than per-module pipelines, so we use path filters to keep a `web/` change from triggering the Android build. There's no single artifact version, so releases are tagged per module (`backend/v1.4.0`, `web/v1.2.0`).