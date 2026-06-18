# Epic 3 — Real-time Admin Dashboard rules

This page captures the per-FR rules for Epic 3 (FR3.1–FR3.2) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs).

## FR3.1 — Live metrics

- **Rule:** Total daily transaction count and total daily volume are computed by the dashboard Kafka consumer (off the request thread) and pushed to subscribed `ADMIN` clients over `WS /admin/ws/metrics`. A snapshot for first-paint is exposed at `GET /admin/metrics/live`.
- **Why:** FR3.1 (real-time view); NFR5 (heavy aggregation must not block the money path).
- **Enforced in:** `dashboard/adapter/in/messaging` aggregator; `dashboard/adapter/in/web` WebSocket push channel; `dashboard/adapter/in/web` snapshot resource. `(verify)`
- **Failure mode:** A consumer crash stops live updates but does not affect the ledger; recovery replays from the last consumer offset since `transaction-events` is retained for replay ([../../project-info.md §4.3](../../project-info.md#43-persistence--data)).
- **Frontend shortcut:** Admin dashboard renders the snapshot immediately and switches to WebSocket-driven updates once the socket attaches.

## FR3.2 — Fraud alert stream

- **Rule:** Fraud alerts are pushed to subscribed `ADMIN` clients over `WS /admin/ws/alerts` as toast notifications, without a page reload.
- **Why:** FR3.2 — operations must react in real time. *(MVP defers `FRAUD_ANALYST` and the analyst UI — see ADR #9; admin temporarily covers the read side.)*
- **Enforced in:** `dashboard/adapter/in/messaging` subscribes to `fraud-alerts`; `dashboard/adapter/in/web` fans out to authenticated WebSocket sessions. `(verify)`
- **Failure mode:** A WebSocket disconnect must not lose alerts permanently — on reconnect, the client requests recent alerts via `GET /fraud/alerts` to backfill `(verify)`.
- **Frontend shortcut:** A bell badge increments per alert; toasts are dismissible but the badge persists until the alert list is viewed.

## RBAC

- **Rule:** Both `WS /admin/ws/alerts` and `GET /admin/metrics/live` enforce the `ADMIN` role at the application service (use case); the WebSocket handshake validates the JWT before accepting the upgrade.
- **Why:** SOC 2 directional least-privilege ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system)). *(MVP defers `FRAUD_ANALYST`; when manual unsuspend / analyst workflows ship, role separation returns via an ADR superseding [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md).)*
- **Enforced in:** `dashboard/adapter/in/web` upgrade handler; `shared/security/`. `(verify)`
- **Failure mode:** WebSocket upgrade rejected with HTTP 403 `error_key: "auth.forbidden"`.
- **Frontend shortcut:** Admin routes are hidden in the navigation for users without an admin role (cosmetic — the server invariant remains).
