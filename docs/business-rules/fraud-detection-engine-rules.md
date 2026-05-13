# Epic 2 — Fraud Detection Engine rules

This page captures the per-FR rules for Epic 2 (FR2.1–FR2.3) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs). Thresholds are tunable via the env vars in [../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles).

## FR2.1 — Velocity check

- **Rule:** Flag an account when more than `FRAUD_VELOCITY_THRESHOLD` (default `5`) committed transactions occur in a sliding window of `FRAUD_VELOCITY_WINDOW_SECONDS` (default `60` s). The check runs on the fraud Kafka consumer, off the request thread.
- **Why:** NFR5 — latency isolation; FR2.1 — detect high-frequency abuse without coupling to the money path.
- **Enforced in:** `fraud/consumer/` velocity rule; `fraud/service/` thresholding. `(verify)`
- **Failure mode:** A flag publishes a `fraud-alerts` event with `rule = "velocity"` and an `evidence` payload that includes the window, the count, and the contributing transaction ids; downstream WebSocket fan-out is the user-visible signal. The originating transaction is **not** rolled back — fraud is reactive, not preventive.
- **Frontend shortcut:** Admin / fraud-analyst dashboard toast badge (FR3.2).

## FR2.2 — Volume check

- **Rule:** Flag an account whose cumulative committed transaction amount (USD-equivalent) crosses `FRAUD_VOLUME_THRESHOLD` (default `50000`) within a sliding window of `FRAUD_VOLUME_WINDOW_SECONDS` (default `3600` s).
- **Why:** FR2.2 — detect bulk movement that velocity alone would miss.
- **Enforced in:** `fraud/consumer/` volume rule; `fraud/service/` thresholding. `(verify)`
- **Failure mode:** Publishes a `fraud-alerts` event with `rule = "volume"` and an `evidence` payload that includes the window and the cumulative sum. Same reactive semantics as FR2.1 — no rollback.
- **Frontend shortcut:** Same as FR2.1.

## FR2.3 — Event publishing

- **Rule:** Every successful wallet movement publishes exactly one event to the `transaction-events` Kafka topic. The HTTP handler does **not** publish to Kafka; publishing is performed by the scheduled outbox poller on a separate thread.
- **Why:** NFR2 (Outbox: no event lost or duplicated relative to DB state); NFR5 (HTTP path stays on the money path only).
- **Enforced in:** `wallet/service/` writes the outbox row inside the same `@Transactional` boundary as the ledger row; `shared/` outbox poller drains the table. `(verify)`
- **Failure mode:** Consumers must be idempotent — at-least-once delivery is the contract. A consumer that double-processes an event corrupts the read model; the de-duplication key is the outbox-event `id`.
- **Frontend shortcut:** None — server invariant.

## Threshold tuning (cross-cutting)

- **Rule:** Threshold and window values are read from env vars at startup and surfaced under `app.fraud.*` in the Quarkus config namespace ([../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles)). Live runtime mutation is **not** in scope for MVP.
- **Why:** Deterministic deploys; auditable rule changes (SOC 2 — change-management traceability via Conventional Commits + PR).
- **Enforced in:** Config classes under `fraud/`. `(verify)`
- **Failure mode:** Missing values cause startup failure; out-of-range values are rejected by Bean Validation.
- **Frontend shortcut:** None.
