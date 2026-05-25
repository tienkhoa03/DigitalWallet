# 0009 — RBAC roles in MVP

## Status

Accepted

## Date

2026-05-25

## Deciders

TBD

## Context

Authorization is role-based ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system)). The original spec posture (three roles `USER` / `ADMIN` / `FRAUD_ANALYST`, with multi-role-per-user via a `role_assignment` table) was driven by SOC 2 least-privilege ambitions and the manual unsuspend workflow under FR2.4: separating `FRAUD_ANALYST` from `ADMIN` keeps fraud-rule-tuning and alert-resolution out of general operations staff's blast radius.

On 2026-05-25, MVP scope was cut: there is no manual unsuspend / fraud-rule-tuning UI in MVP. With no workflow that needs least-privilege separation between admin and analyst, both the dedicated `FRAUD_ANALYST` role and multi-role-per-user become premature complexity. SOC 2 audit-log obligations (the `audit_log` table) were also deferred (see [../../project-info.md §8](../../project-info.md#8-security-baseline)) — without that table, the manual-unsuspend justification flow has nowhere to land. Together, these cuts collapse the role set down to two and the row count per user down to one. Source: [../../project-info.md §10 row 9](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Three roles: `USER`, `ADMIN`, `FRAUD_ANALYST`, with multi-role-per-user via `role_assignment`** — original spec posture. Pros: cleanest SOC 2 least-privilege story; matches FR2 / FR3 boundaries; analysts can hold both `FRAUD_ANALYST` and (e.g.) `USER`. Cons: premature for MVP, where no analyst workflow ships and no `audit_log` table exists to record justification; multi-role-per-user adds a join on every authorization check.
- **Two roles: `USER`, `ADMIN`, stored as a single column on `user.role`** **(chosen)** — simpler; one row per user; analyst reads of the alert stream temporarily covered by `ADMIN`. Cons: when manual unsuspend / fraud-rule tuning ships, both the third role and the multi-role pivot return — that change requires an ADR superseding this one.
- **ABAC (attribute-based)** — flexible but premature for MVP scope.

## Decision

**Two roles in MVP: `USER`, `ADMIN`. The role is stored as a single column on `user.role` (CHECK constraint, default `'USER'`). `FRAUD_ANALYST` and multi-role-per-user (`role_assignment` table) are deferred.**

Concretely:

- The `user` table carries `role varchar NOT NULL CHECK (role IN ('USER','ADMIN')) DEFAULT 'USER'`. See [../database/README.md](../database/README.md) `user`.
- There is **no** `role_assignment` table in MVP. Every user holds exactly one role.
- Endpoints that previously gated on `FRAUD_ANALYST` (the fraud-alert read stream, the analyst un-suspend action) gate on `ADMIN` in MVP, with the analyst-resolution endpoint dropped entirely (see [../api/README.md](../api/README.md) Epic 2 — `POST /fraud/alerts/{alertId}/resolution` is deferred).
- Suspensions are automatic-only (FR2.4); there is no manual unsuspend path in MVP.

## Consequences

- **Easier:** one role check instead of a role-set check on every authorized request; no join on `role_assignment`; signup flow remains simple (no role-grant ceremony); the `user` table fully describes the principal.
- **Harder:** `ADMIN` temporarily covers what would have been `FRAUD_ANALYST`'s reads — this is documented in [../business-rules/real-time-admin-dashboard-rules.md](../business-rules/real-time-admin-dashboard-rules.md) and [../business-rules/fraud-detection-engine-rules.md](../business-rules/fraud-detection-engine-rules.md). The SOC 2 directional least-privilege goal is **softer** until the third role returns.
- **Live with:** no manual unsuspend in MVP. A user flipped to `SUSPENDED` by the async fraud consumer stays suspended until the (deferred) workflow ships. The error key on the sync money path is still `account.suspended` (preserved for API back-compat with the original spec).
- **Revisit if:** any of the following ships — manual unsuspend, fraud-rule tuning UI, an admin role-grant UI, or admin PII reads requiring durable justification. Any one of those reintroduces both the `FRAUD_ANALYST` role and the `audit_log` table together via an ADR superseding this one (and superseding [0010-fraud-enforcement-model.md](0010-fraud-enforcement-model.md) on the un-suspend permission).

## References

- Supersedes nothing. Note: the `FRAUD_ANALYST` role will return via a future ADR superseding this one when manual unsuspend / analyst workflows ship.
- [../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system) — roles in the system.
- [../../project-info.md §8](../../project-info.md#8-security-baseline) — security baseline (audit-log deferral).
- [../database/README.md](../database/README.md) — `user.role` column and MVP scope notes.
- [0010-fraud-enforcement-model.md](0010-fraud-enforcement-model.md) — un-suspend permission (deferred together with this ADR's scope cut).
