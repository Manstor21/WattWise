# ADR-006: Server-initiated push notifications via FCM

**Status:** Accepted (2026-09-12)

## Context

ADR-005 chose local WorkManager notifications and explicitly left FCM as the documented extension path for server-initiated alerts. The product now needs exactly that: an administrator broadcasting a message to every device, or targeting a single user or a specific device (for example "prices spike tomorrow"). The 6-hour WorkManager re-check window of ADR-005 cannot deliver that. This ADR extends ADR-005 rather than replacing it.

## Decision

We add Firebase Cloud Messaging as the push delivery channel for server-initiated notifications, layered on top of the existing local-notification path:

- Backend (Spring Boot): the Firebase Admin SDK (`firebase-admin` 9.9.0) initializes only when `wattwise.firebase.service-account-json-path` (env `FIREBASE_SERVICE_ACCOUNT_PATH`) is configured. The bean is an `Optional<FirebaseMessaging>` — the app boots and runs the same with or without push; the admin send endpoint returns HTTP 503 when no service account is available.
- `push_tokens` table (Flyway V3): per-user device tokens, unique per token, cascade-deleted with the user. `POST /api/push-tokens` (authenticated) registers or reassigns a token to the caller; `DELETE /api/push-tokens/{token}` removes the caller's token; `GET /api/push-tokens` lists the caller's devices.
- `POST /api/push/send` (admin only, `hasRole('ADMIN')`) sends an FCM notification to a single token, to all tokens of a user, or as a broadcast to every registered device — precedence: `token` > `userId` > broadcast. A failure on one token is logged and the rest of the send continues.
- Android (client): `WattwiseFcmService` (a `FirebaseMessagingService`) displays received pushes through the existing `NotificationHelper` channel and re-registers device tokens. `FcmTokenRegistrar` sends the token to the backend after login; a token received before any session exists is stored as pending and flushed on the next login.
- The Firebase Android SDK is pinned with `firebase-bom` 33.7.0 (compatible with the project's compileSdk 34) and the `com.google.gms.google-services` Gradle plugin 4.4.2.

Local WorkManager notifications (ADR-005) are unchanged and remain the primary delivery mechanism for device-computable, offline-first alerts; FCM is only the server-initiated channel. The "FCM deliberately not implemented" note in `NotificationHelper`'s Javadoc is retired.

## Consequences

Positive: the server can reach a device immediately after ESIOS publishes an event; an administrator can broadcast to all registered devices or narrow delivery to one user or device; tokens stay tied to the authenticated user (upsert on registration, cascade-deleted with the user); push is optional at runtime — no service account means HTTP 503 on admin send, never a startup failure; per-token send failures are isolated so a broadcast still reaches the rest.

Negative: operational setup is now required — a Firebase project, a service-account JSON on the backend, and `google-services.json` in `android/app/` (without it the Gradle plugin fails the build); two notification paths must stay coherent (device-local alerts vs server pushes); builds/tests of the Android module need the per-developer `google-services.json` in place.

## References

- ADR-005: Local notifications (WorkManager) instead of FCM push