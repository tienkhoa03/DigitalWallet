---
name: frontend-implement-ui-component
description: Use when the user asks to "add a component", "create a page", "build a UI for", "add a form", "add a view", "add a route", "wire up this API in the frontend", "I need a screen for X", "create a form to submit Z", or any equivalent. Generates Angular 17+ standalone components with signals, Reactive Forms, Tailwind, and a co-located spec file — following the conventions in .claude/rules/frontend_coding.md.
---

# Frontend — Implement UI Component

Scaffold a new Angular component (page, presentational, or form) with the right state management, API wiring, validation, route protection, and a spec file that includes a render test and an XSS regression.

## When NOT to invoke

- Pure styling tweaks to an existing component — edit the file directly.
- Backend-only work — use `backend-create-rest-api`.
- Large-scale state introduction (NgRx, Redux) — that needs an ADR first ([.claude/rules/frontend_coding.md §2.1](../../rules/frontend_coding.md)).

## Step 1 — Gather inputs

One `AskUserQuestion` call. Ask only what isn't clear from the prompt:

| Question | Header |
|---|---|
| Component type? (Page routed at a URL / Presentational dumb / Form-bearing) | Type |
| API endpoints to call (paths + methods)? — must match constants in `core/http/api.ts` | API |
| Auth requirement? (Public / Authenticated wallet user / Admin) | Auth |
| New cross-feature global state needed? (Yes — describe, No) | State |

Then ask for form fields as free-text: `name : type : required? : validators : backend rule it mirrors`. The "backend rule it mirrors" is mandatory per [frontend §4.2](../../rules/frontend_coding.md) — if the user can't name one, stop and ask them to add it on the server first.

## Step 2 — Confirm placement

Print the planned files and the route path (if any). Confirm before writing.

For a form page in `wallet`:

```
frontend/src/app/features/wallet/pages/transfer-form/
├── transfer-form.component.ts
├── transfer-form.component.html
└── transfer-form.component.spec.ts

frontend/src/app/features/wallet/services/transfer.service.ts        # if not already present
frontend/src/app/features/wallet/models/transfer-request.model.ts    # if not already present
frontend/src/app/features/wallet/wallet.routes.ts                    # add a route entry
```

If the frontend module isn't scaffolded yet, stop and tell the user — this skill does not bootstrap `package.json` or `angular.json`.

## Step 3 — Generate files

Apply the rules. Do not paraphrase them.

### Component (`*.component.ts`)

- Standalone, signals API ([frontend §1.1](../../rules/frontend_coding.md)).
- `input.required<T>()` for inputs, never `@Input()` decorator.
- `inject(...)` for services, not constructor injection (current Angular idiom).
- `takeUntilDestroyed()` on every long-lived subscription.

```ts
@Component({
  standalone: true,
  selector: 'dw-transfer-form',
  imports: [ReactiveFormsModule, MoneyAmountPipe],
  templateUrl: './transfer-form.component.html',
})
export class TransferFormComponent {
  private fb = inject(NonNullableFormBuilder);
  private transferService = inject(TransferService);

  loading = signal(false);
  form = this.fb.group({
    fromWallet: ['', [Validators.required]],
    toWallet:   ['', [Validators.required]],
    amount:     [0,  [Validators.required, Validators.min(0.0001)]],
  });

  submit() {
    const key = crypto.randomUUID();
    this.loading.set(true);
    this.transferService
      .transfer(this.form.getRawValue(), key)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: () => this.loading.set(false),
        error: () => this.loading.set(false),
      });
  }
}
```

### Template (`*.component.html`)

- Tailwind utility classes only.
- Every interactive element has a `data-test` attribute ([frontend §11](../../rules/frontend_coding.md)).
- Every input has a `<label>` association ([frontend §17](../../rules/frontend_coding.md)).
- Errors via `[class.invalid]="ctrl.invalid"` — never DOM manipulation.
- Lists via `@for (item of items(); track item.id)` — never `track $index` for filterable lists.

### Service (if new)

- `@Injectable({ providedIn: 'root' })`.
- API paths as constants from `core/http/api.ts` — never literal strings.
- Returns `Observable<T>`; loading state is the component's signal, not the service's.

### Spec (`*.component.spec.ts`)

Required tests:

1. **Render** — component creates without error.
2. **Form happy-path** — fill valid values, submit, assert the service is called with the right shape and `Idempotency-Key` header.
3. **Form invalid** — at least one field violates a validator; submit button is disabled.
4. **XSS regression** ([testing.md §3.6](../../rules/testing.md)) — feed `<script>alert(1)</script>` into any user-controlled rendered text and assert the DOM contains it as text, not as an executed script.

Use `By.css('[data-test="…"]')` or accessible role queries — never class selectors.

### Route registration (if a page)

Add the route to the feature's `*.routes.ts` and apply the appropriate guard from the role table in [frontend §5.3](../../rules/frontend_coding.md).

## Step 4 — Self-check

- [ ] Component is standalone; no `NgModule`.
- [ ] All inputs use the signal API (`input()` / `input.required()`).
- [ ] No `@Input()` decorator added in this change.
- [ ] No literal API path string anywhere — all routed through `core/http/api.ts`.
- [ ] Reactive Forms only (no `ngModel`).
- [ ] Every form field's validator has a matching backend rule cited (frontend §4.2 table updated if a new field-rule pair is introduced).
- [ ] No `*ngFor` / `*ngIf` / `*ngSwitch` in new templates — `@for` / `@if` / `@switch` only.
- [ ] No `localStorage`/`sessionStorage` for auth tokens.
- [ ] Every interactive element has a `data-test` attribute and a label/role.
- [ ] Spec includes the XSS regression.
- [ ] No `console.log` outside `core/error/global-error-handler.ts`.
- [ ] Subscriptions use `takeUntilDestroyed()`.

## Step 5 — Final report

Print:

- Files created (full paths).
- Route added (if any).
- Backend rule each form validator mirrors (table format, even if the user already supplied this — confirm it's recorded).
- Open follow-ups (e.g., "auth scheme not yet committed → guard is a stub").
- Suggest invoking `frontend-verify` next.
