# 0006 — Multi-currency model

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

DigitalWallet supports multi-currency wallets (FR1.1, FR1.3 in [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs)). Two shapes are common: one wallet per currency (cheap balances; FX applied per-transfer) or one wallet with a balance dictionary (single account abstraction; FX applied at read time). Live FX is explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)). The team needs an ADR capturing which shape is preferred and where the FX rate is sourced. Source: [../../project-info.md §10 row 6](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **One wallet per currency; cross-currency transfers convert at transfer time using a cached FX rate** — explicit per-currency balances; FX only impacts the transfer leg, never revalues stored balances.
- **Single wallet, multi-currency balance dictionary** — fewer rows but every read becomes a conversion; harder to audit per-currency state.
- **External FX provider with live rates** — explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)).

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
