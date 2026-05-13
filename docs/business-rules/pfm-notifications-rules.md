# Epic 5 — PFM Notifications rules

This page captures the per-FR rules for Epic 5 (FR5.1, FR5.2) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs).

## FR5.1 — Threshold breach alerts

- **Rule:** When the spent amount on a bucket crosses its configured `threshold_percent` of `planned_amount`, a `pfm-threshold-alerts` Kafka event is emitted and fanned out to the owning user over `WS /users/ws/notifications`. Each breach fires at most one notification per (bucket, month) — a re-crossing after a threshold change is permitted `(verify policy)`.
- **Why:** FR5.1 — soft, single-fire user notifications; consistent with NFR5 (off the request thread) and NFR7 (event-time correct).
- **Enforced in:** `pfm/consumer/` threshold checker; `pfm/service/` notification publisher; `pfm/ws/` user fan-out. `(verify)`
- **Failure mode:**
  - A breach that fires twice for the same (bucket, month) is a regression — caught by an integration test that replays the same event `(verify)`.
  - If the user has no active WebSocket session, the notification is enqueued for next-connect delivery `(verify — push provider not yet committed)`.
- **Frontend shortcut:** The budget view subscribes to the WebSocket while open; missed notifications are surfaced via a notifications drawer `(verify)`.

## FR5.2 — Predictive burn-rate warning

- **Rule:** A predictive warning fires when the bucket's current burn rate, projected linearly to month-end, would exceed `planned_amount`. The example phrasing in [../../project-info.md §5 FR5.2](../../project-info.md#epic-5-pfm-notifications) is "At this pace, your Food budget runs out before day 20".
- **Why:** FR5.2 — give users a forward-looking signal before they breach the hard plan.
- **Enforced in:** `pfm/consumer/` projection rule; uses event-time elapsed since the start of the month (NFR7), not wall-clock. `(verify)`
- **Failure mode:**
  - Projection algorithm is at least monotonic in spent amount — a follow-up event must not retract a fired warning unless explicit refunds are credited `(verify policy)`.
  - At most one predictive warning per (bucket, month) `(verify)`.
- **Frontend shortcut:** Predictive warnings render with a distinct visual treatment so users can distinguish them from FR5.1 breaches `(verify)`.

## Cross-cutting

- **Rule:** All notifications carry the bucket's owning `account_id` in the WebSocket auth context; the server-side fan-out only delivers to sockets owned by that account. No cross-user leakage.
- **Why:** PII isolation — even category-level spending data is sensitive ([../../project-info.md §8](../../project-info.md#8-security-baseline)).
- **Enforced in:** `pfm/ws/` user-scoped channel. `(verify)`
- **Failure mode:** Mis-routed notification is a P0 incident.
- **Frontend shortcut:** None — server invariant.
