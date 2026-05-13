# 0007 — Build tool

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

The backend is Quarkus 3.x on Java 21 ([../../project-info.md §4.1](../../project-info.md#41-backend)). Quarkus supports both Maven and Gradle, and the choice influences archetype availability, example coverage, plugin ergonomics, and the CI baseline. The team needs an ADR capturing the chosen tool and the reasons it was preferred. Source: [../../project-info.md §10 row 7](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Maven** — Quarkus first-class, widest archetype/example coverage.
- **Gradle** — flexible build DSL, faster incremental builds on large modules.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
