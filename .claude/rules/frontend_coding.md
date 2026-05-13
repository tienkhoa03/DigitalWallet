# Frontend coding rules

This file is the coding contract for the React app under `frontend/` (single app serving both end users and the admin dashboard, per [../../project-info.md §3.1](../../project-info.md#31-module--package-organization)). The `code-review` skill checks pull requests against the sections below.

> **Status:** the codebase is not yet scaffolded. Every rule cites either `docs/` or a section of [../../project-info.md](../../project-info.md). Code examples use module names from [../../docs/architecture/README.md §4](../../docs/architecture/README.md#4-frontend-layering). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

Stack (mandated): React 18.x, TypeScript 5.x **strict**, Tailwind CSS 3.x, Redux Toolkit (incl. RTK Query), React Hook Form + Zod, native WebSocket API, pnpm, Vitest + React Testing Library, Playwright. Source: [../../project-info.md §4.2](../../project-info.md#42-frontend), [../../docs/decisions/0008-frontend-stack.md](../../docs/decisions/0008-frontend-stack.md).

## 1. Component structure

- **Paradigm:** functional components only. Class components are forbidden in new code.
- **File naming:** `kebab-case` files; the default-export component is `PascalCase`. A file MUST export at most one component as its `default`; named exports for helpers / subcomponents are allowed.
- **Suffixes:** `<name>.tsx` for components, `<name>.hook.ts` for hooks, `<name>.slice.ts` for Redux slices, `<name>.api.ts` for RTK Query slices, `<name>.schema.ts` for Zod schemas, `<name>.test.tsx`/`<name>.test.ts` for co-located specs.
- **Directory layout** `<!-- not-yet-adopted -->`:

  ```
  frontend/
  ├── src/
  │   ├── app/              # routed shell, providers, store wiring
  │   ├── features/         # one folder per backend module
  │   │   ├── wallet/
  │   │   ├── transfer/
  │   │   ├── fraud/
  │   │   ├── pfm/
  │   │   ├── advisor/
  │   │   └── dashboard/
  │   ├── shared/           # ui primitives, hooks, money, idempotency, websocket client
  │   └── routes/           # route table & guards
  ├── public/
  └── tests/                # cross-feature E2E specs (Playwright)
  ```

  Feature folders mirror the backend modules in [../../docs/architecture/README.md §3](../../docs/architecture/README.md#3-backend-layering). A feature folder MUST NOT import from another feature folder — cross-feature reuse goes through `shared/`.

## 2. State management

| State boundary | Tool | Rule |
|---|---|---|
| Local UI (toggles, input focus, disclosure) | `useState` / `useReducer` | Default for any state that does not need to be shared. MUST NOT promote to Redux out of habit. |
| Cross-component within a feature | Redux Toolkit feature slice (`<feature>.slice.ts`) | One slice per feature; selectors live next to the slice. |
| Cross-feature / app-wide | Redux Toolkit root slice or RTK Query cache | Keep cross-feature global state to a minimum (auth, current user, websocket connection status). |
| Server cache | RTK Query API slice (`<feature>.api.ts`) | All REST traffic goes through RTK Query. MUST NOT hand-roll `fetch` calls inside components. |
| Form state | React Hook Form | Forms MUST NOT push field-level state into Redux. |

- Redux Toolkit is the only state library ([../../project-info.md §4.2](../../project-info.md#42-frontend)). MUST NOT introduce Zustand, Jotai, MobX, Recoil, etc.
- Selectors MUST be memoised with `createSelector` if they produce derived data; raw inline selectors that return new object literals every call are a defect (cause re-renders).

## 3. API calls

- **Network client:** RTK Query. The canonical endpoint catalog lives in [../../docs/api/README.md](../../docs/api/README.md); RTK Query API slices mirror it.
- **API path constants:** every endpoint MUST be declared as a typed constant in `<feature>/<feature>.api.ts` — Never hard-code paths inline at the call site.
- **Per-call shape:** `endpoint` definitions MUST declare both `query` (request shape) and `transformResponse` if the wire shape differs from the UI shape. Loading and error states come from RTK Query (`isLoading`, `isFetching`, `error`) — Never re-implement them.
- **Error normalization:** the global `baseQuery` MUST translate the backend error envelope (`{ error_key, message }` per [../../docs/api/README.md](../../docs/api/README.md#error-response-shape)) into a typed `ApiError` consumed by UI code.
- **Loading state:** components MUST render an explicit loading element while `isLoading` is true. A blank component during fetch is a defect.
- **Token-expiry handling:** the RTK Query `baseQuery` MUST detect a 401 response, dispatch a logout action, and redirect to `/login`. MUST NOT silently retry the request. See [security.md §6](security.md#6-sessions--token-handling-frontend).
- **Idempotency-Key:** mutating endpoints (deposit, withdraw, transfer) MUST attach an `Idempotency-Key` header generated client-side (see §13). Resubmits of the same logical action reuse the same key.

## 4. Forms & validation

- **Library:** React Hook Form + Zod ([../../project-info.md §4.2](../../project-info.md#42-frontend)).
- **Validation MUST be backend-aligned.** The Zod schema MUST mirror the server-side rule referenced below for each form:

  | Form | Field | Zod rule | Backend rule source |
  |---|---|---|---|
  | Open wallet | `currency_code` | ISO 4217 (3 letters); not already owned | [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.1 |
  | Deposit / Withdraw | `amount` | `BigDecimal`-safe string, `> 0` | [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.2 |
  | Transfer | recipient identity | `account_number` XOR `user_id`, not equal to sender wallet's account | [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.3 |
  | Transfer | `amount`, `currency_code` | `amount > 0`; currency is ISO 4217 | [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.3 |
  | Create budget | `month` | ISO `YYYY-MM-01`; unique per account | [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) FR4.1 |
  | Bucket threshold | `threshold_percent` | integer in `[1, 100]` | [../../docs/business-rules/ai-driven-personal-finance-management-rules.md](../../docs/business-rules/ai-driven-personal-finance-management-rules.md) FR4.3 |
  | Statement filter | `from`, `to` | `from <= to`; type ∈ `deposit`/`withdraw`/`transfer_debit`/`transfer_credit` | [../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.4 |
  | Advisor request | `month` | ISO `YYYY-MM-01`; month must be closed | [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) FR6.1 |

- Validation UI is driven by the form library; MUST NOT manipulate the DOM directly (`document.querySelector`, `.classList.add('error')`, etc.) to show validation feedback.
- The frontend Zod schema is a UX courtesy; the backend remains the authoritative validation layer (see [backend_coding.md §12](backend_coding.md#12-validation)). A field passing the Zod schema does not exempt it from server validation.

## 5. Routing & route protection

- **Router:** React Router (v6+). Route definitions live in `src/routes/`.
- **Guards:** functional only — implemented as wrapper components (`<RequireAuth>`, `<RequireRole role="ADMIN">`). MUST NOT mix imperative redirects scattered inside page components with declarative guards.
- **Role / condition map:**

  | Route family | Allowed roles | Source |
  |---|---|---|
  | `/wallets/*`, `/budgets/*`, `/advisor/*` | `USER` | [../../docs/api/README.md](../../docs/api/README.md) Epics 1, 4, 6 |
  | `/admin/dashboard`, `/admin/metrics` | `ADMIN` | [../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) FR3.1 |
  | `/admin/alerts` | `ADMIN`, `FRAUD_ANALYST` | [../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md) RBAC |
  | `/admin/fraud/*` | `FRAUD_ANALYST` | [../../docs/api/README.md](../../docs/api/README.md) Epic 2 |
  | `/login`, `/signup` | public | [../../docs/api/README.md](../../docs/api/README.md) Epic 1 |

- The frontend guard is a UX optimisation; server-side RBAC is authoritative ([../../docs/business-rules/real-time-admin-dashboard-rules.md](../../docs/business-rules/real-time-admin-dashboard-rules.md), [security.md §3](security.md#3-authorization)). Hiding a link is not security.

## 6. Styling

- **Library:** Tailwind CSS 3.x ([../../project-info.md §4.2](../../project-info.md#42-frontend)). MUST NOT add a second CSS-in-JS or CSS-Modules system.
- **Design tokens:** colours, spacing, and typography are defined in `tailwind.config.ts` under `theme.extend`. Ad-hoc hex values in JSX are a defect — extend the theme instead.
- **Responsive utilities:** mobile-first; use Tailwind responsive prefixes (`sm:`, `md:`, `lg:`). Fixed pixel widths on layout containers are forbidden for non-modal content.
- **UI library overrides:** if a shared UI primitive is wrapped, the wrapper lives in `shared/ui/` and exposes a typed prop surface — Never `dangerouslySetInnerHTML` for styling.
- **Money / number formatting:** use the shared helper (`shared/money/format.ts`) — see §13. MUST NOT call `Number.toFixed(2)` directly on monetary values.

## 7. Import ordering

Imports MUST be grouped and ordered as follows, with one blank line between groups:

1. React / Node built-ins.
2. Third-party packages.
3. Project-absolute imports (`@/shared`, `@/features/...`).
4. Project-relative imports (`./...`, `../...`).
5. Style / asset imports (`./foo.css`, `@/assets/...`).

Example `<!-- not-yet-adopted -->`:

```ts
import { useEffect } from 'react';

import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useDepositMutation } from '@/features/wallet/wallet.api';
import { formatMoney } from '@/shared/money/format';

import { DepositSchema } from './deposit.schema';

import './deposit.css';
```

ESLint's `import/order` rule MUST enforce this — manual ordering is not the contract.

## 8. Constants

- **Naming:** `UPPER_SNAKE_CASE` for primitives, `PascalCase` for typed records / enums.
- **Location:** feature-local constants in `<feature>/<feature>.constants.ts`; cross-cutting in `shared/constants.ts`.
- **Environment config:** read from `import.meta.env.VITE_*` (Vite convention) inside `shared/config.ts` and re-export a typed object. Components MUST NOT read `import.meta.env` directly.
- **No magic numbers / strings:** literal monetary thresholds, retry counts, timeouts, and rate-limit hints MUST be named constants. Same for `errorKey` strings consumed in error-handling UI — declare them in a const map.

## 9. Async patterns

- **Promise vs Observable:** Promises (and async/await). Project MUST NOT introduce RxJS — RTK Query handles streaming cache invalidation.
- **WebSocket subscriptions** subscribe through the shared client in `shared/websocket/`. Components subscribe via a hook (`useWebSocketChannel(channelName)`); the hook MUST unsubscribe on unmount. See §13 and §15.
- **Cleanup:** every `useEffect` that opens a subscription, timer, or `fetch` MUST return a cleanup function. An effect without cleanup that owns external state is a defect.
- **Race-condition guards:** when an effect depends on a value that may change, the cleanup MUST cancel or ignore the in-flight result. Use `AbortController` for `fetch` or a captured "stale" flag for non-cancellable work.

## 10. Props / Component arguments

- **Typed:** every component prop MUST have a TypeScript type. `any` is forbidden in production code; `unknown` is acceptable at trust boundaries (e.g. parsing a JSON message from WebSocket) and must be narrowed before use.
- **Discriminated unions** are preferred to boolean flags for mutually exclusive prop combinations (`{ kind: 'create' } | { kind: 'edit', id: string }`).
- **No prop drilling beyond 3 levels.** Reach for context, Redux, or RTK Query before passing a prop through five components.
- **Function props:** stable identity. Pass `useCallback`-wrapped handlers to memoised children; otherwise React.memo is pointless.

## 11. Testing

- See [testing.md §3](testing.md#3-frontend-testing) for the full frontend testing contract.
- **Co-location:** test files live next to the unit under test (`deposit-form.tsx` + `deposit-form.test.tsx`).
- **Query priority:** queries SHOULD prefer in this order:
  1. `data-test="..."` attributes (explicit contract — `getByTestId`).
  2. Accessibility role / name (`getByRole`).
  3. Visible text (`getByText`).
  Class names and CSS selectors are NEVER acceptable as test queries.
- **Network:** mock at the RTK Query level (`msw` or `setupApiStore`) — Never stub global `fetch`.

## 12. Shared components catalogue

`<!-- not-yet-adopted -->` This table is the inventory of primitives that MUST be reused rather than re-implemented per feature. Add a row when a new shared primitive lands.

| Component | Path | Purpose |
|---|---|---|
| `MoneyDisplay` | `shared/ui/money-display.tsx` | Formats `numeric(19,4)` amounts with currency code. |
| `IdempotencyKeyField` | `shared/ui/idempotency-key-field.tsx` | Hidden field that emits a UUIDv7 per logical submission. |
| `WebSocketProvider` | `shared/websocket/provider.tsx` | App-wide WebSocket client; one connection per session. |
| `RequireAuth` / `RequireRole` | `routes/guards.tsx` | Route-level RBAC. |
| `ErrorBoundary` | `shared/ui/error-boundary.tsx` | Route-level fallback (see §14). |
| `ToastShelf` | `shared/ui/toast-shelf.tsx` | Fraud alert toasts (FR3.2) and threshold notifications (FR5.1). |

## 13. Domain-specific conventions

- **Idempotency-Key generation:** the deposit / withdraw / transfer forms MUST generate a UUIDv7 client-side at form mount and reuse the same key across resubmissions of the same logical action ([../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.2). A fresh navigation creates a fresh key. The shared helper lives in `shared/idempotency/`.
- **Money formatting:** monetary values arrive as decimal strings (to preserve `numeric(19,4)` precision). The shared `formatMoney(value, currencyCode)` helper MUST be used — it relies on `Intl.NumberFormat` with the currency code from the value. MUST NOT parse money into JS `number` before display (precision loss).
- **Currency selection:** the wallet-opening form filters out currencies already owned by the account ([../../docs/business-rules/core-wallet-management-rules.md](../../docs/business-rules/core-wallet-management-rules.md) FR1.1, "Frontend shortcut"). This is cosmetic — the server invariant remains the source of truth.
- **Real-time stream subscription:** the app MUST use a **single** shared WebSocket connection per session. Per-feature channels (`/admin/ws/alerts`, `/admin/ws/metrics`, `/users/ws/notifications`, advisor reply) are multiplexed through the shared client — Never open multiple raw `new WebSocket(...)` instances.
- **Async advisor reply:** the "Get advice" button transitions to a spinner on HTTP 202 keyed by `request_id`; the resolution arrives over WebSocket ([../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) FR6.1, FR6.2). Stale replies (user navigated away and back) are reconciled against the last known `request_id`.
- **Circuit-open UI:** when the backend returns `errorKey: "advisor.circuit_open"`, the advisor view MUST render a clear "Advisor unavailable, try later" state. MUST NOT degrade to a rule-based heuristic ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope), [../../docs/business-rules/ai-advisor-rules.md](../../docs/business-rules/ai-advisor-rules.md) Cross-cutting rule on circuit breaker).
- **Rate-limit feedback:** on `429 ratelimit.exceeded`, UI MUST honour the `Retry-After` header to disable the submit button for that interval.

## 14. Error boundaries

- A top-level `ErrorBoundary` MUST wrap the routed shell.
- Each top-level route MUST have its own boundary so that a feature's failure does not blank the whole app.
- Boundaries MUST log via the shared error reporter — Never `console.error` ([../../docs/business-rules/README.md](../../docs/business-rules/README.md) Cross-cutting security rules, no PII in logs, applies to the browser console as well).
- A boundary MUST NOT swallow errors silently; the user-facing fallback shows "something went wrong" plus a recovery action (retry, navigate home).

## 15. Framework-specific lifecycles

- **Effects:** dependencies are exhaustive (enforced by `eslint-plugin-react-hooks/exhaustive-deps`). Suppressing the rule MUST come with an inline comment explaining why; suppression without justification is a defect.
- **Cleanup:** subscriptions (WebSocket, intervals, RTK Query manual subscriptions) MUST be cleaned up.
- **Race-condition guards:** when fetching data based on a route param that may change before the response arrives, the effect MUST cancel via `AbortController` or guard the assignment with a captured "stale" flag.
- **`useLayoutEffect`** is reserved for measurements that must complete before paint; default to `useEffect`.

## 16. List rendering

- **Stable keys:** every `.map` of JSX MUST use a stable id (`transaction.id`, `bucket.id`) as `key`. Array index keys are forbidden for lists whose order can change.
- **Virtualization:** lists of more than ~200 rows MUST use a virtualizer (e.g. `@tanstack/react-virtual`). Transaction statements (FR1.4) and fraud alert backlogs are the primary candidates.

## 17. Accessibility floor

The WCAG target is open ([../../project-info.md §17.3](../../project-info.md#173-accessibility-floor)); the rules below are the non-negotiable baseline that holds regardless.

| Concern | Rule |
|---|---|
| Keyboard reachability | Every interactive element MUST be reachable by `Tab` and operable by `Enter`/`Space`. Custom controls without keyboard handlers are a defect. |
| Labels | Every form input MUST have an associated `<label>` (explicit `htmlFor` or wrapping label). |
| ARIA | ARIA attributes used to compensate for non-semantic markup MUST come in pairs (e.g. `role="dialog"` + `aria-modal` + focus trap). |
| Contrast | Text against background MUST clear WCAG AA contrast — verified via Tailwind colour pairings, not custom hex values. |
| Live regions | Real-time toasts (fraud alerts, budget alerts) MUST be announced via an `aria-live="polite"` region; silent updates are a defect for non-sighted users. |
| Images | Every meaningful image MUST have `alt`; decorative images use `alt=""`. |

## 18. Bundle hygiene

- **devDependencies isolation:** test, lint, and storybook packages MUST live under `devDependencies`. A devDependency imported from production code is a defect.
- **Strip console logs:** the production build MUST drop `console.log` / `console.debug` (via Vite plugin or build flag). `console.error` is allowed only inside the shared error reporter.
- **Tree-shake imports:** use named imports from large libraries (`import { format } from 'date-fns'`, not `import * as df from 'date-fns'`). MUST NOT import an entire icon library — pick the icons used.
- **No runtime polyfills for browsers below the target.** Vite's browserslist is the source of truth.

## 19. Anti-patterns

| Don't | Why | Do instead |
|---|---|---|
| Hand-rolled `fetch` inside a component | Bypasses RTK Query caching, retry, and error normalization. | Define an RTK Query endpoint in `<feature>.api.ts`. |
| Storing form field values in Redux | Re-render storm and lost focus. | Use React Hook Form; promote to Redux only on submit. |
| `any` typed state, props, or return | Defeats TypeScript strict mode. | Use precise types or `unknown` narrowed at the boundary. |
| `useEffect` to derive state from props | Causes extra renders and stale state bugs. | Compute the derived value in render or with `useMemo`. |
| Index as `.map` key for orderable lists | Breaks reconciliation, eats focus. | Use a domain-stable id. |
| Direct `document.*` manipulation in components | Hides reactive state from React. | Use refs or controlled state. |
| Subscribing to WebSocket in many places | Reconnect storm, duplicate handlers. | Use the shared WebSocket client (§13). |
| Showing money via `Number.toFixed(2)` | Precision loss on `numeric(19,4)`. | Use `formatMoney` from `shared/money/`. |
| Generating a fresh `Idempotency-Key` per retry | Defeats NFR3 — retries become new transactions. | Reuse the per-submission key (see §13). |
| Hiding the admin link instead of guarding the route | UX trick, not security. | Guard via `<RequireRole>` and let the server reject unauthorised calls ([security.md §3](security.md#3-authorization)). |
| Magic monetary numbers in component bodies | Drifts across files; impossible to tune centrally. | Move to `<feature>.constants.ts` (§8). |
| Inline Tailwind colours via arbitrary `[#ab12cd]` | Defeats design tokens. | Extend `tailwind.config.ts` and reference the token (§6). |
| `console.log` left in committed code | Leaks PII, noisy in prod ([security.md §1](security.md#1-secrets-and-configuration)). | Use the shared error reporter; rely on the build to strip dev logs (§18). |
| One WebSocket connection per route | Connection thrash; missed messages on remount. | Connect once at the shell; subscribe per channel (§13). |
| Catching every error to show a generic toast | Hides root-cause errors; users see the same message everywhere. | Branch on `error_key` (§3) and surface specific messages. |
