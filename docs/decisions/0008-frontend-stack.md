# 0008 — Frontend stack

## Status

Accepted

## Date

2026-06-10

## Deciders

TBD

## Context

The frontend serves both end users and the admin dashboard from a single Vue app ([../../project-info.md §3.1](../../project-info.md#31-module--package-organization)). It must drive REST endpoints, subscribe to WebSocket channels for alerts (FR3.2, FR5.1) and the async LLM reply (NFR8), and submit forms with type-safe validation. The team needs an ADR capturing the whole stack so library choices are not re-litigated per epic. Source: [../../project-info.md §10 row 8](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

## Options considered

- **Vue 3 + TypeScript strict + Tailwind + Pinia + TanStack Query (Vue Query) + VeeValidate + Zod + Vue Router + pnpm + Vitest + `@testing-library/vue` + Playwright** **(chosen)** — Composition API + `<script setup>` SFCs give concise, ergonomic components; Pinia is the official, setup-style store; TanStack Query (Vue Query) supplies the REST caching/invalidation and loading/error data layer; VeeValidate + Zod (`toTypedSchema`) covers schema-validated forms; aligns with the team's chosen direction.
- **React 18 + Redux Toolkit (incl. RTK Query) + React Hook Form + Zod** — battle-tested combination; RTK Query handles REST caching consistently, but the team prefers the Composition API ergonomics and the Pinia + Vue Query data layer.
- **Angular** — full-batteries framework; heavier learning curve for the team `(verify)`.
- **Svelte / SvelteKit** — concise but newer; fewer enterprise references.

## Decision

The frontend is built on **Vue 3.x** (Composition API, `<script setup lang="ts">` single-file components) with **TypeScript 5.x strict** (type-check via `vue-tsc`) and **Tailwind CSS 3.x** for styling. State management is **Pinia** (`defineStore`, setup-style stores; getters for derived data); the server cache and REST data layer is **TanStack Query — Vue Query** (`@tanstack/vue-query`: `useQuery` / `useMutation`, query keys + invalidation). Forms use **VeeValidate + Zod** (`@vee-validate/zod` `toTypedSchema`). Routing uses **Vue Router 4.x** (functional guards on `route.meta`). The package manager is **pnpm**; unit testing is **Vitest + `@testing-library/vue`**; E2E smoke is **Playwright**. The realtime client is the native WebSocket API (one shared connection per session).

## Consequences

- **Easier:** Composition API + `<script setup>` keeps components concise; Pinia setup stores are typed and ergonomic; Vue Query removes hand-rolled `fetch` plumbing and standardises loading/error/invalidation; VeeValidate + `toTypedSchema` keeps form validation aligned with the backend Zod rules.
- **Harder:** the broader Vue ecosystem has fewer ready-made RTK-Query-style integrations, so the shared HTTP client (`shared/api/http.ts`) and WebSocket multiplexing are owned in-house; engineers coming from React must learn the Composition API and the Pinia/Vue Query split.
- **Live with:** two complementary data tools (Pinia for client state, Vue Query for server cache) rather than a single store; the boundary between them is a convention the team maintains.
- **Revisit if:** Vue Query's caching model proves insufficient for the realtime dashboard, Pinia is outgrown by app-wide state needs, or a future SSR/meta-framework requirement (e.g. Nuxt) changes the build posture.

## References

- Related ADRs: [0012-hexagonal-architecture](0012-hexagonal-architecture.md)
- Spec sections: [../../project-info.md §4.2](../../project-info.md#42-frontend), [../../project-info.md §10 row 8](../../project-info.md#10-open-architectural-decisions-adrs-to-write), [../../project-info.md §3.1](../../project-info.md#31-module--package-organization)
- External: Vue 3, Pinia, TanStack Query (Vue Query), VeeValidate, Vue Router, Vitest, `@testing-library/vue`, Playwright documentation.
