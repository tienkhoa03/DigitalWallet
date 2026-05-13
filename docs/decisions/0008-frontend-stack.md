# 0008 — Frontend stack

## Status

Proposed

## Date

2026-05-13

## Deciders

TBD

## Context

The frontend serves both end users and the admin dashboard from a single React app ([../../project-info.md §3.1](../../project-info.md#31-module--package-organization)). It must drive REST endpoints, subscribe to WebSocket channels for alerts (FR3.2, FR5.1) and the async LLM reply (NFR8), and submit forms with type-safe validation. The team needs an ADR capturing the whole stack so library choices are not re-litigated per epic. Source: [../../project-info.md §10 row 8](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **React 18 + TypeScript strict + Tailwind + Redux Toolkit (incl. RTK Query) + React Hook Form + Zod + pnpm + Vitest + Playwright** — battle-tested combination; RTK Query handles REST caching and WebSocket subscriptions consistently.
- **Angular** — full-batteries framework; heavier learning curve for the team `(verify)`.
- **Vue / Nuxt** — competitive ergonomics; smaller ecosystem for the specific RTK-Query-style data layer.
- **Svelte / SvelteKit** — concise but newer; fewer enterprise references.

## Decision

_TBD — to be decided._

## Consequences

- **Easier:** —
- **Harder:** —
- **Live with:** —
- **Revisit if:** —

## References

- —
