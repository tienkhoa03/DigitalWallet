# Epic 2 — Fraud Detection Engine rules

This page captures the per-FR rules for Epic 2 (FR2.1–FR2.5) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs). Thresholds are tunable via the env vars in [../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles).

> **Enforcement model:** the fraud engine is **preventive**, not purely observational ([../../project-info.md §5 Epic 2](../../project-info.md#5-functional-requirements-epics--frs), [../../project-info.md §6 NFR9](../../project-info.md#6-non-functional-requirements--invariants)). Velocity (FR2.1) and volume (FR2.2) checks reject the offending transaction inline on the synchronous money path; the async fraud consumer (FR2.4, FR2.5) owns deeper analysis, analyst alerting, and account-level suspension policy. See [../decisions/0010-fraud-enforcement-model.md](../decisions/0010-fraud-enforcement-model.md).

## FR2.1 — Velocity check

- **Rule:** The sync money path MUST reject any deposit / withdraw / transfer that would push the account past `FRAUD_VELOCITY_THRESHOLD` (default `5`) committed transactions in a sliding window of `FRAUD_VELOCITY_WINDOW_SECONDS` (default `60` s). The check runs **before** the wallet lock is acquired and uses Redis sliding-window counters (per account, per rule).
- **Why:** NFR9 — block visibly fraudulent activity at the edge without coupling the request thread to heavy analytics.
- **Enforced in:** `wallet/service/` fraud pre-check (sync, on the request thread); `fraud/consumer/` for cross-event analysis and counter reconciliation. `(verify)`
- **Failure mode:** Rejection returns HTTP 422 with `errorKey: "fraud.velocity_exceeded"`. The block path opens its own short `@Transactional` boundary that writes one `audit_log` row and one `transaction.blocked` outbox event (no ledger row, no wallet lock). The async consumer (FR2.5) also publishes a `fraud-alerts` event with `rule = "velocity"`.
- **Frontend shortcut:** Admin / fraud-analyst dashboard toast badge (FR3.2); on the user side, the rejection surfaces a clear `fraud.velocity_exceeded` message.

## FR2.2 — Volume check

- **Rule:** The sync money path MUST reject any transaction whose cumulative committed transaction amount (USD-equivalent) would push the account past `FRAUD_VOLUME_THRESHOLD` (default `50000`) within a sliding window of `FRAUD_VOLUME_WINDOW_SECONDS` (default `3600` s). Same Redis-counter model as FR2.1.
- **Why:** FR2.2 — detect bulk movement that velocity alone would miss; NFR9 — preventive enforcement at the edge.
- **Enforced in:** `wallet/service/` fraud pre-check (sync); `fraud/consumer/` for cross-event analysis. `(verify)`
- **Failure mode:** Rejection returns HTTP 422 with `errorKey: "fraud.volume_exceeded"`. Same `audit_log` + `transaction.blocked` outbox semantics as FR2.1. The async consumer (FR2.5) publishes a `fraud-alerts` event with `rule = "volume"` and an `evidence` payload that includes the window and the cumulative sum.
- **Frontend shortcut:** Same as FR2.1.

## FR2.3 — Event publishing

- **Rule:** Every **successful** wallet movement MUST publish exactly one event to the `transaction-events` Kafka topic; every **blocked** transaction (FR2.1, FR2.2, FR2.4) MUST also be persisted (an `audit_log` row plus a `transaction.blocked` outbox event) so analysts retain a full record of denied attempts alongside committed ones. The HTTP handler does **not** publish to Kafka; publishing is performed by the scheduled outbox poller on a separate thread.
- **Why:** NFR2 (Outbox: no event lost or duplicated relative to DB state); NFR5 (HTTP path stays bounded — analytics on Kafka threads); NFR9 (blocked attempts are first-class events for audit and downstream analysis).
- **Enforced in:** `wallet/service/` writes the outbox row inside the same `@Transactional` boundary as the ledger row (success path) or inside a short transaction with no ledger row (block path); `shared/` outbox poller drains the table. `(verify)`
- **Failure mode:** Consumers must be idempotent — at-least-once delivery is the contract. A consumer that double-processes an event corrupts the read model; the de-duplication key is the outbox-event `id`.
- **Frontend shortcut:** None — server invariant.

## FR2.4 — Account suspension

- **Rule:** When an account accumulates `FRAUD_SUSPENSION_BREACH_COUNT` (default `3`) FR2.1 / FR2.2 breaches within `FRAUD_SUSPENSION_WINDOW_SECONDS` (default `3600` s), the async fraud consumer MUST flip `account.fraud_status` from `ACTIVE` to `SUSPENDED`. Subsequent money mutations from a suspended account are rejected by the sync pre-check with HTTP 403 `errorKey: "account.suspended"`. Unblocking is a `FRAUD_ANALYST`-only action; `ADMIN` MUST NOT inherit this permission.
- **Why:** FR2.4 — repeat-breach detection cannot live on the request thread; suspension policy is a cross-event decision owned by the async consumer (NFR5).
- **Enforced in:** `fraud/consumer/` suspension policy; `wallet/service/` pre-check reads `account.fraud_status`; `fraud/api/` for the analyst un-suspend endpoint `(verify)`.
- **Failure mode:** Flipping to `SUSPENDED` writes an `audit_log` row with `action = "fraud.account.suspend"` and an `evidence` payload citing the contributing breach ids. Un-suspending writes `action = "fraud.account.unsuspend"` with the analyst's principal id and a justification field. The state change propagates to the sync path within the §17.1 budget (≤ 1 s).
- **Frontend shortcut:** Admin / fraud-analyst dashboard surfaces suspended accounts as part of the alert stream (FR3.2).

## FR2.5 — Alert stream

- **Rule:** Every rule breach (block under FR2.1 / FR2.2, and every suspension transition under FR2.4) MUST publish a record to Kafka `fraud-alerts` for the admin / fraud-analyst dashboard (FR3.2). Blocking the user does not replace analyst notification — both happen.
- **Why:** FR2.5 — analysts need a unified stream of all detected breaches, regardless of whether the user was already blocked synchronously.
- **Enforced in:** `fraud/consumer/` after the cross-event analysis decides whether to alert / suspend; emission of `fraud-alerts` records is its only output topic. `(verify)`
- **Failure mode:** Publishes a `fraud-alerts` event with `rule ∈ {"velocity","volume","suspension"}` and an `evidence` payload (window, counts, sums, transition state). WebSocket fan-out (FR3.2) is the analyst-visible signal.
- **Frontend shortcut:** Toast badge in the admin / fraud-analyst dashboard.

## Threshold tuning (cross-cutting)

- **Rule:** Threshold and window values are read from env vars at startup and surfaced under `app.fraud.*` in the Quarkus config namespace ([../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles)). Live runtime mutation is **not** in scope for MVP.
- **Why:** Deterministic deploys; auditable rule changes (SOC 2 — change-management traceability via Conventional Commits + PR).
- **Enforced in:** Config classes under `fraud/`. `(verify)`
- **Failure mode:** Missing values cause startup failure; out-of-range values are rejected by Bean Validation.
- **Frontend shortcut:** None.
