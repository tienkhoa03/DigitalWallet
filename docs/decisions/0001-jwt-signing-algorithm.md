# 0001 — JWT signing algorithm

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

DigitalWallet uses stateless JWT authentication ([../../project-info.md §8](../../project-info.md#8-security-baseline)). The signing algorithm determines token size on the wire, signing cost on the wallet path, and the operational shape of the key material (RSA keypair vs. ECDSA P-256 keypair). The team needs an ADR capturing the alternatives considered and the rationale for the chosen algorithm. Source: [../../project-info.md §10 row 1](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **ES256 (ECDSA P-256)** — smaller tokens and keys than RS256, faster signing on the wallet path.
- **RS256 (RSA-SHA-256)** — wider tooling support but larger tokens and slower signing.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
