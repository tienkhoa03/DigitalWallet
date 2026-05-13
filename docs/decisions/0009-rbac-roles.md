# 0009 — RBAC roles in MVP

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

Authorization is role-based ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system)). Operations admins and fraud analysts have different daily needs and different blast radii — collapsing them into one role would over-grant fraud-rule-tuning and alert-resolution privileges to general operations staff and contradict SOC 2 least-privilege ([../../project-info.md §8](../../project-info.md#8-security-baseline)). The team needs an ADR fixing the role set for MVP. Source: [../../project-info.md §10 row 9](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Three roles: `USER`, `ADMIN`, `FRAUD_ANALYST`** — separate `FRAUD_ANALYST` from `ADMIN` so fraud tuning is not implicitly granted to ops; matches FR2 / FR3 boundaries.
- **Two roles: `USER`, `ADMIN`** — simpler but over-grants fraud-rule control to ops.
- **ABAC (attribute-based)** — flexible but premature for MVP scope.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
