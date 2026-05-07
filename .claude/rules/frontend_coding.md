# Frontend Coding — Digital Wallet (Angular 17+)

*How to write Angular 17+ TypeScript code in this repo. Architecture and product behaviour live in [docs/](../../docs/); this file is the implementation contract.*

> **Status:** the frontend module is not yet scaffolded. Every rule cites either [docs/](../../docs/) or a section of the project spec. Code examples use module names from [docs/architecture/README.md §4](../../docs/architecture/README.md). Sections marked `<!-- not-yet-adopted -->` describe practices to follow once code lands.

## 1. Component structure

> See also: [docs/architecture/README.md §4](../../docs/architecture/README.md)

### 1.1 Paradigm

- **Standalone components only.** No `NgModule` for new code (Angular 17 default).
- Use the **signals API** for component-local state (`signal()`, `computed()`, `effect()`). Reserve RxJS for async streams (HTTP, WebSocket).
- Functional guards (`CanActivateFn`), functional interceptors (`HttpInterceptorFn`), and functional resolvers — class-based equivalents are forbidden in new code.

### 1.2 File naming

| Artifact | Suffix | Example |
|---|---|---|
| Component | `.component.ts` | `transfer-form.component.ts` |
| Service | `.service.ts` | `wallet.service.ts` |
| Guard | `.guard.ts` | `admin.guard.ts` |
| Interceptor | `.interceptor.ts` | `auth.interceptor.ts` |
| Pipe | `.pipe.ts` | `money-amount.pipe.ts` |
| Model / interface | `.model.ts` | `wallet.model.ts` |
| Spec | `.spec.ts` | `transfer-form.component.spec.ts` |

Files are kebab-case; classes are PascalCase.

### 1.3 Directory layout

```
frontend/src/app/
├── core/                   # singletons; one provider per file
│   ├── http/               # interceptors, base URL, error normalization
│   ├── ws/                 # WebSocket service for fraud-alerts
│   └── auth/               # auth state, guards (verify scheme)
├── features/
│   ├── wallet/             # deposit, withdraw, transfer, history
│   │   ├── pages/          # routed components
│   │   ├── components/     # presentational
│   │   ├── services/       # feature services
│   │   └── models/         # feature DTO interfaces
│   └── admin/              # metrics dashboard, fraud-alert toasts
└── shared/                 # ui primitives, pipes, layout
```

`core/` is imported once at the root. `features/` may import from `core/` and `shared/`, never from another feature.

---

## 2. State management

### 2.1 Boundary

| State scope | Where it lives |
|---|---|
| Component-local UI (toggle, focus, current step) | `signal()` in the component |
| Cross-component within one feature | feature service exposed via `providedIn: 'root'` or feature-level provider |
| Cross-feature (auth, current user, websocket session) | `core/` service exposed via `providedIn: 'root'` |
| Server cache (transactions list, wallet balance) | feature service holding a `signal()` keyed by ID; refresh on mutation |

NgRx is not introduced for the MVP. `<!-- not-yet-adopted -->` Document the boundary in an ADR before introducing any global store.

### 2.2 Local storage keys

`localStorage` is **opaque to the user**, **shared across tabs**, and **never** appropriate for short-lived secrets. Every read/write goes through `core/storage/local-storage.service.ts`. Direct `localStorage.*` calls are forbidden.

| Key | Purpose | Lifetime | Notes |
|---|---|---|---|
| `dw.idempotency-pending` | Pending transfer's `Idempotency-Key` while a request is in flight | until 2xx response or explicit failure | Cleared on success/failure to avoid leaks across sessions |

> **Auth tokens are intentionally not in this table** — see [security.md §6](security.md).

---

## 3. API calls

### 3.1 Network client

- `HttpClient` from `@angular/common/http`. **Never** `fetch`, **never** `axios`.
- Base URL injected via an `HTTP_BASE_URL` token, configured per environment file.

### 3.2 API path constants

```ts
export const API = {
  WALLETS:        '/wallets',
  TRANSFERS:      '/transfers',
  ADMIN_METRICS:  '/admin/metrics',
} as const;
```

Constants are the only way to construct paths. String literals embedded in components or services are forbidden. Path strings here MUST match [docs/api/README.md](../../docs/api/README.md).

### 3.3 Per-call shape

```ts
@Injectable({ providedIn: 'root' })
export class TransferService {
  private http = inject(HttpClient);

  transfer(req: TransferRequest, idempotencyKey: string): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(API.TRANSFERS, req, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }
}
```

### 3.4 Error handling

Error normalization happens in a single HTTP interceptor; components subscribe to a typed `ApiError` shape:

```ts
export interface ApiError { errorKey: string; message: string; status: number; }
```

`errorKey` values match the table in [backend_coding.md §8.3](backend_coding.md).

### 3.5 Loading state

Every call exposes a `loading` signal alongside the result. Templates render the loader from the signal — never use `*ngIf="data"` to hide a spinner.

### 3.6 Token expiry handling

`<!-- not-yet-adopted -->` On 401, the auth interceptor clears in-memory auth state and routes to login. The user's pending form input is preserved in `sessionStorage` only when no secret fields are involved.

---

## 4. Forms & validation

### 4.1 Library

Reactive Forms (`@angular/forms`) only. Template-driven forms are forbidden in new code.

```ts
this.form = this.fb.nonNullable.group({
  fromWallet: ['', [Validators.required]],
  toWallet:   ['', [Validators.required]],
  amount:     [0,  [Validators.required, Validators.min(0.0001)]],
});
```

### 4.2 Backend-aligned validation

Every client-side validator MUST match a backend rule. The matrix:

| Field | Frontend rule | Backend rule | Source |
|---|---|---|---|
| `amount` | `Validators.min(0.0001)` | `@DecimalMin("0.0001")` | [wallet-rules.md](../../docs/business-rules/wallet-rules.md#deposit-amount--0) |
| `fromWallet != toWallet` | cross-field validator | service guard | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#sender--receiver) |
| `Idempotency-Key` | UUID v4 generated client-side | header required | [transfer-rules.md](../../docs/business-rules/transfer-rules.md#idempotency-key-required) |

A frontend validator that has no backend counterpart is a defect — the server is the trustworthy boundary.

### 4.3 No DOM manipulation for validation UI

Errors are rendered via `[class.invalid]="form.controls.x.invalid"` bindings. **Never** `document.querySelector` to add error classes — it breaks SSR and tests.

---

## 5. Routing & route protection

### 5.1 Router

Standalone Router config (`app.routes.ts`). Use `loadComponent` / `loadChildren` for lazy loading.

```ts
export const routes: Routes = [
  { path: 'wallet', loadChildren: () => import('./features/wallet/wallet.routes') },
  { path: 'admin',  canMatch: [adminGuard], loadChildren: () => import('./features/admin/admin.routes') },
  { path: '**', redirectTo: '' },
];
```

### 5.2 Guards

- Functional `CanActivateFn` and `CanMatchFn` only.
- Class-based `CanActivate` / `Resolve` are legacy and forbidden in new code.

### 5.3 Role / condition mapping

| Route | Required condition | Guard |
|---|---|---|
| `/wallet/**` | authenticated wallet user | `authGuard` |
| `/admin/**` | authenticated admin | `adminGuard` |

The auth scheme is unspecified — guard implementations are stubs until [docs/decisions/](../../docs/decisions/) commits to one.

---

## 6. Styling

### 6.1 Strategy

> See also: [docs/architecture/README.md §2](../../docs/architecture/README.md).

- **Tailwind CSS** is the styling layer. Most components ship without a `.scss` file — utility classes in the template only.
- Component-scoped CSS only when Tailwind cannot express the rule (deep custom animations, third-party widget overrides).

### 6.2 Variables and tokens

Design tokens (color, spacing, breakpoints) live in `tailwind.config.js`. **Never** hard-code hex colors in templates.

### 6.3 Responsive

Tailwind breakpoint utilities (`sm:`, `md:`, `lg:`). **Never** raw `@media` rules in component CSS.

### 6.4 UI library overrides

If a third-party widget is introduced, its overrides live in a single CSS file under `shared/styles/overrides/`. `<!-- not-yet-adopted -->`

---

## 7. Import ordering

Every `.ts` file orders imports as:

1. Angular packages (`@angular/*`).
2. RxJS (`rxjs`, `rxjs/operators`).
3. Other third-party packages (alphabetical).
4. App-internal absolute paths from `src/app/core/...`.
5. App-internal absolute paths from `src/app/shared/...`.
6. App-internal absolute paths from `src/app/features/...` (rare; usually a smell across features).
7. Relative imports (`./`, `../`).

Blank line between groups.

```ts
import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { LocalStorageService } from 'src/app/core/storage/local-storage.service';
import { LoaderComponent } from 'src/app/shared/ui/loader/loader.component';

import { TransferRequest } from './models/transfer-request.model';
```

`<!-- not-yet-adopted -->` Enforce via ESLint `import/order`.

---

## 8. Constants

### 8.1 Naming

`UPPER_SNAKE_CASE` for primitive constants. `as const` for object/tuple literals.

### 8.2 Environment config

`src/environments/environment.ts`, `environment.local.ts`, etc. Components and services read environment values via injection tokens — never directly. **Anything in `environment.*.ts` ships to the browser** — see [security.md §1.3](security.md).

### 8.3 No magic numbers

Every threshold (idempotency-key TTL, polling interval, retry count) is a named constant in `core/constants/`. A literal `60000` in a component is a defect.

---

## 9. Async patterns

### 9.1 async/await vs Observables

- `Observable` for HTTP, WebSocket, and any stream that emits more than once.
- `Promise` / `async-await` for one-shot async (storage operations, navigation).
- Do not mix `async-await` with subscription chains — convert one or the other consistently within a function.

### 9.2 Subscription lifecycle

Use `takeUntilDestroyed()` (Angular 17+) for any long-lived subscription:

```ts
this.alertStream$
  .pipe(takeUntilDestroyed())
  .subscribe(alert => this.handleAlert(alert));
```

A `subscribe()` without a teardown story is a memory leak and a code-review block.

---

## 10. Props / Component Arguments

### 10.1 Inputs

Use the signal-based input API (Angular 17+):

```ts
@Component({ ... })
export class TransferFormComponent {
  fromWalletId = input.required<string>();
  amount       = input<number>(0);
}
```

### 10.2 Typing

- Every `input`/`output` is typed. **Never** `any`.
- `unknown` is acceptable as an explicit "narrow before use" — `any` is a code-review block.

### 10.3 Destructuring

Prefer property access over destructuring component inputs in templates. Destructure in `.ts` only when narrowing types.

---

## 11. Testing

> See also: [testing.md](testing.md).

- Spec files live next to the component (`*.spec.ts`).
- Use Angular's `TestBed` with standalone-component imports.
- Query elements with `By.css('[data-test="…"]')` or accessible role/name. **Never** by class name or DOM structure — both are styling decisions, not contracts.
- Add a `data-test` attribute to every interactive element a test depends on.

---

## 12. Shared components catalogue

`<!-- not-yet-adopted -->` Populate this table as components are written. Until then, the entries below are spec-anticipated.

| Component | Import path | Use case |
|---|---|---|
| `LoaderComponent` | `src/app/shared/ui/loader/loader.component` | Full-page or inline spinner with accessible label |
| `MoneyAmountPipe` | `src/app/shared/pipes/money-amount.pipe` | Render `BigDecimal` strings as locale currency |
| `FraudAlertToastComponent` | `src/app/features/admin/components/fraud-alert-toast.component` | Red toast for `fraud-alerts` payloads |
| `IdempotencyKeyDirective` | `src/app/core/http/idempotency-key.directive` | Generates and persists a UUID for a form's submit cycle |

---

## 13. Domain-specific conventions

### 13.1 Idempotency-Key generation

The transfer form generates a fresh UUID v4 on submit, holds it in memory until the response, and reuses it across retries until 2xx. See [transfer-rules.md](../../docs/business-rules/transfer-rules.md#idempotency-key-required).

```ts
const key = crypto.randomUUID();
```

A new UUID per submit attempt that **succeeds** at the server; the same UUID across retry attempts of the **same** submit.

### 13.2 Money formatting

All amount displays go through `MoneyAmountPipe`. **Never** `Number.toFixed()` or `Intl.NumberFormat` directly in a template.

### 13.3 WebSocket fraud alerts

The fraud-alert stream is a single shared `core/ws/fraud-alert.service.ts`. Components subscribe to its `alerts$` observable. **Never** open a second WebSocket per component.

---

## 14. Error boundaries

### 14.1 Global handler

A custom `ErrorHandler` (`core/error/global-error-handler.ts`) logs to the console (dev) and to a remote sink (prod). It MUST NOT swallow errors silently.

### 14.2 Route-level fallback

Each top-level route renders a `<error-fallback>` for resolver and load failures. The user always sees a route — never a blank page.

---

## 15. Framework-specific lifecycles

### 15.1 Effects

`effect()` only for side effects driven by signals. **Never** mutate a signal that the same effect reads (infinite loop).

### 15.2 Cleanup

`afterNextRender` / `afterRender` for DOM-touching code; `OnDestroy` for imperative teardown when `takeUntilDestroyed()` does not fit (closing a non-RxJS resource).

### 15.3 Race-condition guards

A component that issues an HTTP call from a route param uses `switchMap` so a navigation away cancels the in-flight request:

```ts
this.route.params.pipe(
  switchMap(({ id }) => this.walletService.get(id)),
  takeUntilDestroyed(),
).subscribe(wallet => this.wallet.set(wallet));
```

---

## 16. List rendering

### 16.1 Stable keys

Use `@for (item of items(); track item.id)`. **Never** `track $index` for lists that change order or are filtered — silent rendering bugs.

### 16.2 Performance

Lists over ~50 rows: prefer `*cdkVirtualFor` (Angular CDK) over rendering everything. `<!-- not-yet-adopted -->`

---

## 17. Accessibility (A11y) floor

Non-negotiable. A merge that fails any of these is rejected.

| Rule | How to verify |
|---|---|
| Every interactive element is keyboard-reachable in tab order | Tab through the page; no `tabindex="-1"` on user controls |
| Every input has an associated `<label>` | linter rule + manual review |
| ARIA only when semantic HTML is insufficient | Prefer `<button>` over `<div role="button">` |
| Color contrast ≥ AA (4.5:1 normal, 3:1 large) | Lighthouse / Axe |
| Focus outline is visible | Never `outline: none` without a custom replacement |
| Toasts and live regions use `aria-live` | `polite` for metrics, `assertive` for fraud alerts |

---

## 18. Bundle hygiene

### 18.1 DevDependencies isolation

Test-only utilities (`@testing-library/*`, fixtures) live in `devDependencies` and never appear in non-spec `.ts` files.

### 18.2 Strip console logs

`console.log` is forbidden in committed code outside of `core/error/global-error-handler.ts`. Use `LoggerService` (a thin wrapper) so prod builds drop debug output. `<!-- not-yet-adopted -->`

### 18.3 Import optimization

Tree-shake-friendly imports only:

```ts
import { map } from 'rxjs/operators';     // good
// import * as ops from 'rxjs/operators'; // forbidden
```

---

## 19. Anti-patterns

| Don't | Why | Do instead |
|---|---|---|
| `NgModule` for new code | Angular 17 default is standalone | Standalone component, route-based lazy load |
| Class-based `HttpInterceptor` | Verbose, deprecated for new code | `HttpInterceptorFn` |
| `localStorage.setItem('token', …)` | Token theft via XSS | See [security.md §6](security.md) |
| `subscribe()` without teardown | Memory leak | `takeUntilDestroyed()` |
| `(click)="document.getElementById…"` | DOM coupling, breaks SSR | Template binding via `signal()` |
| Hard-coded API base URL | Breaks env switching | `HTTP_BASE_URL` injection token |
| `*ngFor` (legacy) | Slower, no `track` enforcement | `@for (… ; track id)` |
| `track $index` on filterable lists | Silent rendering bugs on reorder | `track item.id` |
| `console.log` in committed code | Leaks data, fails bundle hygiene | `LoggerService` |
| Form rule that does not match backend | Server-side rejection looks like a UI bug | Update both sides; record in §4.2 |
| Two WebSocket connections to the same topic | Wasted resources, duplicate alerts | One `core/ws/` service, fan out via signal |
