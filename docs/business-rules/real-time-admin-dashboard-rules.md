# Epic 3 — Real-time Admin Dashboard rules

This page captures the per-FR rules for Epic 3 (FR3.1–FR3.2) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs).

## FR3.1 — Live metrics

- **Rule:** Total daily transaction count and total daily volume are computed by the dashboard Kafka consumer (off the request thread) and pushed to subscribed `ADMIN` clients over `WS /admin/ws/metrics`. A snapshot for first-paint is exposed at `GET /admin/metrics/live`.
- **Why:** FR3.1 (real-time view); NFR5 (heavy aggregation must not block the money path).
- **Enforced in:** `dashboard/consumer/` aggregator; `dashboard/ws/` push channel; `dashboard/api/` snapshot resource. `(verify)`
- **Failure mode:** A consumer crash stops live updates but does not affect the ledger; recovery replays from the last consumer offset since `transaction-events` is retained for replay ([../../project-info.md §4.3](../../project-info.md#43-persistence--data)).
- **Frontend shortcut:** Admin dashboard renders the snapshot immediately and switches to WebSocket-driven updates once the socket attaches.

## FR3.2 — Fraud alert stream

- **Rule:** Fraud alerts are pushed to subscribed `ADMIN` and `FRAUD_ANALYST` clients over `WS /admin/ws/alerts` as toast notifications, without a page reload.
- **Why:** FR3.2 — operations and fraud analysts must react in real time.
- **Enforced in:** `dashboard/consumer/` subscribes to `fraud-alerts`; `dashboard/ws/` fans out to authenticated WebSocket sessions. `(verify)`
- **Failure mode:** A WebSocket disconnect must not lose alerts permanently — on reconnect, the client requests recent alerts via `GET /fraud/alerts` to backfill `(verify)`.
- **Frontend shortcut:** A bell badge increments per alert; toasts are dismissible but the badge persists until the alert list is viewed.

## RBAC

- **Rule:** Both `WS /admin/ws/alerts` and `GET /admin/metrics/live` enforce the `ADMIN` or `FRAUD_ANALYST` role at the service layer; the WebSocket handshake validates the JWT before accepting the upgrade.
- **Why:** SOC 2 — least privilege; the `FRAUD_ANALYST` role is intentionally separated from `ADMIN` ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system)).
- **Enforced in:** `dashboard/ws/` upgrade handler; `shared/security/`. `(verify)`
- **Failure mode:** WebSocket upgrade rejected with HTTP 403 `error_key: "auth.forbidden"`.
- **Frontend shortcut:** Admin routes are hidden in the navigation for users without an admin role (cosmetic — the server invariant remains).
