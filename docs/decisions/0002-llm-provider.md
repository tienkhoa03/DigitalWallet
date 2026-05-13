# 0002 — LLM provider for the PFM advisor

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

The PFM advisor (Epic 6, [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs)) makes outbound LLM calls wrapped in a SmallRye circuit breaker (NFR8) and returns personalised advice over the asynchronous request-reply channel. The choice of provider blocks every FR6.x acceptance criterion: it sets the prompt format, retry budget, rate-limit math, and — critically — the data-retention / training-opt-out policy that drives the anonymisation contract in [../../project-info.md §8](../../project-info.md#8-security-baseline) and [../../project-info.md §16 item 15](../../project-info.md#16-open-questions-to-answer-before-bootstrapping). Source: [../../project-info.md §10 row 2](../../project-info.md#10-open-architectural-decisions-adrs-to-write) — the only open ADR.

## Options considered

- **Anthropic Claude** — frontier model with strong safety tooling.
- **OpenAI** — large model family, broadest tooling ecosystem.
- **Google Gemini** — competitive frontier model.
- **Local model** — eliminates retention concerns at the cost of operational burden.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
