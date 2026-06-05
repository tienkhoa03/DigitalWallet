# 0010 — Fraud enforcement model

## Status

Accepted

## Date

2026-05-25

## Deciders

TBD

## Context

The original spec ([../../project-info.md §5 Epic 2](../../project-info.md#5-functional-requirements-epics--frs) and §6 NFR5, pre-2026-05-18) cast the fraud engine as **purely observational** — velocity (FR2.1) and volume (FR2.2) checks ran in a Kafka consumer thread and produced `fraud-alerts` for the admin dashboard, but the offending transaction was already committed to the ledger by the time the alert fired.

Stakeholder review on 2026-05-18 rejected that posture as insufficient: in a wallet platform, a transaction that is already on the ledger is, for most practical purposes, "money moved." Detecting fraud after the commit lets a single suspicious account drain or pump value before an analyst can react. The spec was amended to require **synchronous blocking** at the edge (new NFR9) while keeping the existing async consumer for cross-event analysis, alerting (renumbered FR2.5), and account-level suspension policy (new FR2.4). See [../../project-info.md §6 NFR9](../../project-info.md#6-non-functional-requirements--invariants), [../../project-info.md §5 Epic 2](../../project-info.md#5-functional-requirements-epics--frs), and [../../project-info.md §10 row 10](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

The change has trade-offs against NFR5 (latency isolation): the HTTP path now carries a fraud step on the critical path. NFR5 was therefore softened to permit fast, bounded Redis-counter pre-checks while preserving the ban on heavy analytics inline. The performance budget for `POST /transfers` (≤ 200 ms P95, [../../project-info.md §17.1](../../project-info.md#171-performance-budget)) is kept; the pre-check is bounded to two Redis sliding-window lookups plus one `account.fraud_status` read.

## Options considered

- **Async-only (status quo before 2026-05-18)** — fraud runs entirely in the Kafka consumer; alerts surface in the admin dashboard but the offending transaction is already committed. Pros: maximum latency isolation; simplest NFR5 story. Cons: cannot prevent the first abusive transaction; analysts must claw back value after the fact; product unacceptable for a wallet.
- **Sync-only** — every fraud rule, including cross-event aggregation and the suspension-policy decision, runs on the request thread. Pros: strongest guarantee that nothing reaches the ledger before checking. Cons: violates NFR5 entirely; cross-event analysis on the request thread couples the money path to unbounded work; analyst alerts become a side-effect of the request handler.
- **Hybrid: sync pre-check (counters + status read) + async consumer (analysis + policy + alerts)** **(chosen)** — the request thread runs only bounded, O(1) checks against Redis counters and a single `account.fraud_status` read; the Kafka consumer owns cross-event analysis, repeat-breach detection, suspension policy, and the `fraud-alerts` topic. Pros: blocks visibly fraudulent activity at the edge; keeps heavy analytics off the request thread; preserves NFR5 in spirit. Cons: two systems to keep in sync (Redis counters and the async consumer must agree on what counts as a breach); the suspension state change has propagation delay (≤ 1 s per §17.1).
- **Synchronous "fail-open" advisory check** — run the pre-check inline but only log warnings (never block). Pros: zero latency risk to the money path. Cons: same outcome as async-only — the transaction commits anyway.

## Decision

Adopt the **hybrid** model. The sync money path runs a bounded pre-check (velocity FR2.1 + volume FR2.2 Redis sliding-window counter lookups + `account.fraud_status` read FR2.4) before the wallet lock; a breach rejects inline with HTTP 422 `fraud.velocity_exceeded` / `fraud.volume_exceeded` or HTTP 403 `account.suspended` (error key preserved for API back-compat). Blocked attempts persist a `transaction.blocked` outbox event in a short `@Transactional` boundary (no ledger row, no wallet lock). Cross-event analysis, alert fan-out (FR2.5), and the suspension-policy decision (when to flip a user to `SUSPENDED` — FR2.4) remain on the Kafka consumer.

> **MVP scope cut (2026-05-25):** the manual unsuspend workflow is **deferred** together with the `FRAUD_ANALYST` role (see [0009-rbac-roles.md](0009-rbac-roles.md)) and the `audit_log` table (see [../../project-info.md §8](../../project-info.md#8-security-baseline)). MVP ships **auto-suspension only**: the async consumer flips `account.fraud_status` to `SUSPENDED` per FR2.4, and the user remains suspended. The blocked-attempt `audit_log` row is also deferred — only the `transaction.blocked` outbox event is persisted. When manual unsuspend ships, this ADR is superseded together with [0009-rbac-roles.md](0009-rbac-roles.md).

## Consequences

- **Easier:** prevention at the edge — the worst-case fraud scenario (an attacker draining a wallet faster than analysts can react) is no longer possible; the user-visible response is immediate and typed (`fraud.velocity_exceeded` / `fraud.volume_exceeded` / `account.suspended`) so the UI can render a clear message.
- **Harder:** two systems must agree on what counts as a breach — Redis counters drive the sync decision, the async consumer drives policy. Counter reconciliation (e.g. after a Redis flush) needs a rebuild path from the materialized history of committed transactions and `transaction.blocked` outbox events.
- **Live with:** the `/transfers` P95 budget now includes the pre-check. The pre-check is intentionally bounded (two Redis ops + one DB read); the §17.1 note flags ~10 ms P95 as the trigger to revisit the counter implementation before relaxing the budget. Suspension state propagation has ≤ 1 s delay (§17.1 row), so a small race window exists between an async `SUSPENDED` flip and the next sync request.
- **Revisit if:** the §17.1 fraud-pre-check budget is breached in observability data; the breach-count / window thresholds (FR2.4) prove tunable enough to be runtime-mutable (currently startup-only env vars); a future requirement adds a third rule that cannot be expressed as a sliding-window counter.

## References

- Related ADRs: [0003-concurrency-strategy](0003-concurrency-strategy.md) (Redis-lock + DB row-lock order), [0005-outbox-publisher](0005-outbox-publisher.md) (outbox publish semantics — the `transaction.blocked` event uses the same path), [0009-rbac-roles](0009-rbac-roles.md) (MVP role set — `FRAUD_ANALYST` un-suspend permission deferred together with this ADR's manual-unsuspend leg).
- Spec sections: [../../project-info.md §5 Epic 2](../../project-info.md#5-functional-requirements-epics--frs), [../../project-info.md §6 NFR5 / NFR9](../../project-info.md#6-non-functional-requirements--invariants), [../../project-info.md §8](../../project-info.md#8-security-baseline) (audit-log deferred, suspension privilege), [../../project-info.md §14](../../project-info.md#14-environment--configuration) (`app.fraud.suspension.*` env vars), [../../project-info.md §16 row 16](../../project-info.md#16-open-questions-to-answer-before-bootstrapping) (un-block policy — auto-only in MVP), [../../project-info.md §17.1](../../project-info.md#171-performance-budget) (performance budget).
- Business rules: [../business-rules/fraud-detection-engine-rules.md](../business-rules/fraud-detection-engine-rules.md).
