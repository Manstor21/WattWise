# ADR-005: Local notifications (WorkManager) instead of FCM push

**Status:** Accepted (2026-03-04)

## Context

The Android app's core job is to tell the user "the cheap window for your washer starts at 03:00 — want to schedule it?". That's a reminder about a slot that is already known: the phone has the 96 PVPC prices cached in Room from the last sync, and `OptimalWindowScheduler` can compute the next window entirely offline. The trigger is time-based and computed on the device itself. That is fundamentally different from push use cases — "someone liked your post", "your ride is arriving" — where the server holds information the client doesn't. Server push wasn't solving a real problem here.

## Decision

We schedule notifications locally. A periodic `PriceCheckWorker` (WorkManager, every 6h) refreshes cached prices when connectivity allows, recomputes the next optimal window per appliance, and fires a `NotificationCompat` notification via `NotificationHelper`. No `google-services.json`, no Firebase project, no FCM token lifecycle, no server-side device registry.

## Consequences

Positive: notifications work fully offline — which the offline-first design already requires (see ADR-002) — and the mechanism is pure Android SDK, with no third-party runtime in the loop; device tokens never leave the phone, keeping the privacy story simple. It also removed a whole class of development pain: no per-developer Firebase project, no test devices fighting with GCM registration.

Negative: we cannot reach the device the moment ESIOS publishes a surprise change outside a recomputation window — bounded by WorkManager re-checking every 6h, acceptable for a day-ahead market. There's also no cross-device notification sync. FCM stays in the README roadmap as the documented extension path if the product ever adds server-initiated alerts, e.g. a "prices spike tomorrow" push.