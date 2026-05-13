---
name: frontend-implement-ui-component
description: Scaffold a new React + TypeScript component (route page, presentational, or form) under `frontend/` — including Redux Toolkit / RTK Query wiring, React Hook Form + Zod validation, route guards, and a Vitest + React Testing Library spec. Invoke when the user asks to "create a new component for X", "add the wallet/deposit/transfer page", "scaffold the form for X", "add a new route", "wire up the API call for X", "build the UI for X", "add a presentational component", or "implement the screen for X".
---

# Frontend — Implement UI Component

This skill scaffolds a single React component plus its dependencies (slice, API endpoint, Zod schema, route guard, spec). It cites the frontend coding contract instead of restating it.

## When NOT to invoke

- The user wants to edit an existing component's logic — edit directly.
- The user wants to add a new global state primitive unrelated to a component — write a slice in `src/app/` directly per [frontend_coding.md §2](../../rules/frontend_coding.md).
- The user wants a backend endpoint — use `backend-create-rest-api` instead.
- The user wants verification (lint / build / test) — use `frontend-verify`.

## Step 1 — Gather inputs

Confirm `frontend/` is scaffolded by checking for `frontend/package.json` and `frontend/tsconfig.json`. If they do not exist, **stop** and tell the user — this skill does not bootstrap the project (the stack is mandated in [project-info.md §4.2](../../../project-info.md) and ADR #8).

Read the target feature folder under `frontend/src/features/<feature>/` to learn its existing slice, API, and route conventions per [frontend_coding.md §1](../../rules/frontend_coding.md).

Then gather missing inputs in one `AskUserQuestion` call:

1. **Component type** — `Route page` / `Presentational component` / `Form`. Drives whether a route guard, RTK Query hook, or React Hook Form schema is wired.
2. **API endpoints** — REST paths (must mirror [docs/api/README.md](../../../docs/api/README.md)); marks mutating endpoints that need `Idempotency-Key` per [frontend_coding.md §3](../../rules/frontend_coding.md) and [frontend_coding.md §13](../../rules/frontend_coding.md).
3. **Auth requirement** — public / `USER` / `ADMIN` / `FRAUD_ANALYST` per [frontend_coding.md §5](../../rules/frontend_coding.md).
4. **Form fields** (forms only) — names, types, and which backend rule each one mirrors (see the table in [frontend_coding.md §4](../../rules/frontend_coding.md)). Skip the question for non-form components.

Also gather (free-form, only if not obvious from the prompt): the **feature folder** (must mirror a backend module per [frontend_coding.md §1](../../rules/frontend_coding.md)) and whether the component needs new **global state** beyond the feature slice.

## Step 2 — Confirm placement

Print the planned file list and halt for confirmation:

```
frontend/src/features/<feature>/
├── <component-name>.tsx
├── <component-name>.test.tsx
├── <component-name>.schema.ts        (forms only — Zod schema per frontend_coding.md §4)
├── <feature>.slice.ts                (new/extended — per frontend_coding.md §2)
├── <feature>.api.ts                  (new/extended — per frontend_coding.md §3)
└── <feature>.constants.ts            (new — per frontend_coding.md §8)
frontend/src/routes/
└── routes.tsx                        (extended — per frontend_coding.md §5)
```

Halt if:

- The target feature folder does not exist — feature folders mirror backend modules per [frontend_coding.md §1](../../rules/frontend_coding.md); if missing, ask the user to confirm creating it.
- A cross-feature import would be required — [frontend_coding.md §1](../../rules/frontend_coding.md) forbids feature-to-feature imports; route reuse through `shared/`.

## Step 3 — Write files

Generate in dependency order:

1. **API slice** (`<feature>.api.ts`) — every endpoint declared as a typed constant; `query` and `transformResponse`; `Idempotency-Key` header for mutating endpoints; 401 handling via the shared `baseQuery`. Per [frontend_coding.md §3](../../rules/frontend_coding.md), [frontend_coding.md §13](../../rules/frontend_coding.md), and [security.md §6](../../rules/security.md).
2. **Slice** (`<feature>.slice.ts`) — `createSlice`; selectors memoised with `createSelector`. Per [frontend_coding.md §2](../../rules/frontend_coding.md) and [upgrade-policy.md §4](../../rules/upgrade-policy.md). Skip if the component does not need shared feature state.
3. **Zod schema** (`<component-name>.schema.ts`, forms only) — each rule mirrors the backend rule referenced in [frontend_coding.md §4](../../rules/frontend_coding.md). Cite the specific business-rules document for each field (e.g. [docs/business-rules/core-wallet-management-rules.md](../../../docs/business-rules/core-wallet-management-rules.md) for wallet/transfer fields, [docs/business-rules/ai-driven-personal-finance-management-rules.md](../../../docs/business-rules/ai-driven-personal-finance-management-rules.md) for budget fields, [docs/business-rules/ai-advisor-rules.md](../../../docs/business-rules/ai-advisor-rules.md) for advisor fields).
4. **Constants** (`<feature>.constants.ts`) — endpoint paths, retry counts, rate-limit hints, `errorKey` strings. Per [frontend_coding.md §8](../../rules/frontend_coding.md).
5. **Component** (`<component-name>.tsx`) — functional component; default-export `PascalCase`; `kebab-case` file; import order per [frontend_coding.md §7](../../rules/frontend_coding.md); Tailwind tokens (no arbitrary hex) per [frontend_coding.md §6](../../rules/frontend_coding.md); `formatMoney` for monetary values per [frontend_coding.md §13](../../rules/frontend_coding.md); explicit loading state per [frontend_coding.md §3](../../rules/frontend_coding.md); stable keys in lists per [frontend_coding.md §16](../../rules/frontend_coding.md); single WebSocket connection per [frontend_coding.md §13](../../rules/frontend_coding.md); accessibility floor per [frontend_coding.md §17](../../rules/frontend_coding.md).
6. **Route registration** (`routes/routes.tsx`) — wrap in `<RequireAuth>` / `<RequireRole>` per [frontend_coding.md §5](../../rules/frontend_coding.md). Server-side RBAC is authoritative per [security.md §3](../../rules/security.md); the route guard is a UX hint.
7. **Spec** (`<component-name>.test.tsx`) — see Step 4.

## Step 4 — Required spec tests

Generate these tests at minimum per [frontend_coding.md §11](../../rules/frontend_coding.md) and [testing.md §3](../../rules/testing.md):

- **Render** — component mounts and shows its loading-then-content paths. Use `findBy*` for async assertions per [testing.md §3](../../rules/testing.md).
- **Form happy path** (forms only) — valid submission triggers the RTK Query mutation and renders success state.
- **Form invalid** (forms only) — invalid input surfaces the field-level error from the Zod schema and prevents submission.
- **XSS regression** — for any component that renders user-supplied free text, assert a string containing `<script>` is rendered as text, not executed. Per [testing.md §3](../../rules/testing.md) and [security.md §11](../../rules/security.md).

Conventions: co-locate the test with the component; query priority `data-test` → role → text per [frontend_coding.md §11](../../rules/frontend_coding.md); mock at the RTK Query level (msw / `setupApiStore`), never stub global `fetch`; render through the shared `renderWithProviders` helper per [testing.md §3](../../rules/testing.md).

## Step 5 — Report

Report:

- The files written.
- Where the request was adjusted (e.g. "moved field validation into a Zod schema instead of inline JSX checks per [frontend_coding.md §4](../../rules/frontend_coding.md)").
- Any rule that flagged a conflict (e.g. cross-feature import, missing `shared/` primitive).
- Backend rules each form field mirrors, with citations into `docs/business-rules/`.
- Suggested next step: invoke `frontend-verify` to run lint, build, and tests.
