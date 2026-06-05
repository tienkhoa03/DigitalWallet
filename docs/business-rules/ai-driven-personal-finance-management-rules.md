# Epic 4 — AI-Driven Personal Finance Management rules

This page captures the per-FR rules for Epic 4 (FR4.1–FR4.3) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs).

## FR4.1 — Multi-bucket budgeting

- **Rule:** An account holds at most one budget per calendar month. The budget is implicitly scoped to the account's immutable `base_currency` (no per-budget currency column). Each budget owns a set of buckets, one per category, each with a `planned_amount` and an optional `threshold_percent` (see FR4.3). `month` is a `date` of the form `YYYY-MM-01` (CHECK constraint enforces day = 1).
- **Why:** Models the user-facing "monthly plan" mental model in [../../project-info.md §9](../../project-info.md#9-domain-glossary); enforced by the schema in [../database/README.md](../database/README.md).
- **Enforced in:** `pfm/service/` budget service; DB `UNIQUE(account_id, month)` on `budget` and `UNIQUE(budget_id, category_id)` on `budget_bucket`. `(verify)`
- **Failure mode:** HTTP 409 `error_key: "budget.duplicate_month"` or `error_key: "budget.duplicate_category"`.
- **Frontend shortcut:** The budget editor groups buckets visually by category and disables already-used categories on the picker.

## FR4.2 — Real-time bucket updates

- **Rule:** The PFM Kafka consumer drains `transaction-events` and updates the spent amount on the matching bucket — keyed by `(account_id, month_of(event_timestamp), category_id)`. Cross-currency spending is converted to the account's `base_currency` using the snapshotted `transaction.exchange_rate` on the event payload (never a live lookup). Updates go to the **Redis hot read-model**; the Postgres materialized view is refreshed on a schedule as the durable backup and rebuild source. Direct `UPDATE`s against ledger tables are forbidden (NFR6).
- **Why:** NFR5 (off the request thread) + NFR6 (CQRS dual read-model) + NFR7 (event time, not wall-clock).
- **Enforced in:** `pfm/consumer/` updater; Redis hash schema in `pfm/persistence/`; scheduled MV refresh + Redis rebuild job. `(verify)`
- **Failure mode:**
  - Missing `category_id` on the transaction → no bucket update; the transaction is recorded in the ledger normally.
  - Late event arriving after a month boundary → routed to the previous month's bucket based on `event_timestamp`, not wall-clock (NFR7).
  - Redis loss → the consumer pauses while the Redis rebuild job replays from the MV; consumer offsets are not advanced past the rebuild point `(verify)`.
- **Frontend shortcut:** The budget view subscribes to `WS /accounts/ws/notifications` for threshold breaches; for the headline spent-amount delta it polls `GET /budgets/{month}` lazily.

## FR4.3 — Threshold setup

- **Rule:** Users may set a soft warning level on a bucket as a percentage of `planned_amount`. The threshold is integer-valued in the range `[1, 100]`. Hitting the threshold raises a notification (see [pfm-notifications-rules.md](pfm-notifications-rules.md)) but does **not** block subsequent transactions.
- **Why:** Soft warnings preserve user autonomy — the system advises, it does not police spending.
- **Enforced in:** `pfm/service/` threshold updater; Bean Validation on the DTO. `(verify)`
- **Failure mode:**
  - Out-of-range value → HTTP 400 `error_key: "validation.invalid_threshold"`.
  - Threshold change while the threshold is already breached → the next breach evaluation re-arms; previously-fired notifications are not re-sent `(verify)`.
- **Frontend shortcut:** The threshold input is a slider clamped to `[1, 100]`.

## Cross-cutting

- **Rule (event time):** Every PFM aggregation uses `event_timestamp` from the Kafka payload, not the consumer's wall-clock or DB-insert time.
- **Why:** NFR7 — late events must not corrupt accounting reports.
- **Enforced in:** `pfm/consumer/`. `(verify)`
- **Failure mode:** A consumer that uses wall-clock time is a regression — caught by an integration test that injects an out-of-order event `(verify)`.
- **Frontend shortcut:** None — server invariant.

- **Rule (no direct ledger UPDATE):** PFM logic must never issue `UPDATE` against `transaction`, `wallet`, or `outbox_event`. Bucket state is maintained only in Redis (hot) + the materialized view (durable backup).
- **Why:** NFR6 — prevent contention on the money path; preserve ACID guarantees.
- **Enforced in:** Module-boundary review; `pfm/persistence/` has no JPA repository on ledger entities. `(verify)`
- **Failure mode:** Code review rejection; an integration test asserts `pfm/` writes only target Redis and the MV refresh path `(verify)`.
- **Frontend shortcut:** None.
