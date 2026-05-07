# Business Rules

> **Not to be confused with `.claude/rules/`**, which contains coding-style and AI-enforcement rules. This folder documents **product behaviour** — what the running app must accept, reject, or log.

> **Status:** all entries are derived from [../../README.md §3 (FR)](../../README.md) and [§4 (NFR)](../../README.md). They are **(spec — not yet implemented)** until the corresponding code lands; replace `(verify)` placeholders with concrete file paths once that happens.

## Index

| File | Topic |
|---|---|
| [transfer-rules.md](transfer-rules.md) | Idempotency, locking, validation for `POST /transfers`. |
| [wallet-rules.md](wallet-rules.md) | Account / wallet creation, deposit, withdrawal. |
| [fraud-rules.md](fraud-rules.md) | Velocity and volume detection in the Kafka consumer. |

## Where each rule is enforced

| Rule | Frontend check | Backend check | DB constraint |
|---|---|---|---|
| Transfer amount > 0 | client-side input validator (verify) | DTO validation (verify) | `CHECK (amount > 0)` (verify) |
| Sender ≠ receiver | client-side prevention (verify) | service-layer guard (verify) | none |
| Sender has sufficient funds | optimistic UI hint (verify) | **server-only** — checked under `SELECT … FOR UPDATE` lock | `CHECK (balance >= 0)` (verify) |
| Both wallets exist & active | optional UI lookup (verify) | service-layer fetch | FK on `transaction_history.wallet_id` |
| `Idempotency-Key` required on `POST /transfers` | client always generates one | **server-only** — reject 400 if missing | n/a |
| `Idempotency-Key` deduplicates retries | client reuses key on retry | server checks Redis before processing | n/a (Redis), or unique index if persisted |
| Velocity: ≤ 5 txns / wallet / minute | n/a (admin-only signal) | **fraud consumer only** — not on request path | n/a |
| Volume: ≤ $50,000 / wallet / hour cumulative | n/a | **fraud consumer only** — not on request path | n/a |

## Convention — adding a new rule

1. **Implement at the server boundary first** — DTO validation or service-layer guard. The server is the only trustworthy enforcement point.
2. **Add a DB constraint** when the invariant must hold even if the service code regresses (e.g., `balance >= 0`).
3. **Add a client-side shortcut** only after the server guard exists, and only as a UX nicety — never as a security boundary.
4. **Document the rule here** — add a row to the matrix above and a section in the appropriate topic file.
