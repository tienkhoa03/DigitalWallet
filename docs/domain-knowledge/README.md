# Domain knowledge

This page is the product-side framing of DigitalWallet — what the system is, who uses it, and the vocabulary it commits to.

## 1. What the product is

DigitalWallet is a multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager (PFM) that learns from each user's spending stream ([../../project-info.md §1](../../project-info.md#1-project-identity)). The primary value the project sets out to prove is that a single event-driven backbone can serve both a strict ACID money ledger **and** a derived, AI-augmented analytics stream without coupling them on the request thread. The product addresses two audiences in parallel in MVP: end users who deposit, withdraw, transfer, and want guidance on their own spending; and operations admins who need a live view of transaction volume and the fraud-alert stream ([../../project-info.md §2.1](../../project-info.md#21-user-personas)).

> **MVP scope cut.** A separate fraud-analyst persona / `FRAUD_ANALYST` role and the manual unsuspend workflow are deferred — admin temporarily covers the read side of fraud alerts. See [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md).

## 2. Core domain concepts

Source: [../../project-info.md §9](../../project-info.md#9-domain-glossary).

| Term | Meaning in this product |
|---|---|
| User | A signed-up identity; owns one or more wallets, one or more budgets, and zero or more fraud alerts. In MVP a user holds exactly one role (`USER` or `ADMIN`) on the `user.role` column. (Spec earlier called this "Account" — renamed to `user` in the schema; the API contract still uses `account.suspended` as the error key for backward compat.) |
| Base currency | An ISO 4217 code chosen by the user at signup and stored as `user.base_currency`. **Immutable.** Every budget owned by this user is implicitly scoped to it. Cross-currency spending is converted by the PFM consumer using the `exchange_rate` snapshotted on the `transaction` row. |
| Wallet | A balance-bearing record owned by a user, scoped to a single currency. A user MAY own multiple wallets in the same currency (no uniqueness on `(user_id, currency)`); siblings are disambiguated by a user-supplied `label`. |
| Transfer | A two-leg atomic operation: debit the sender wallet, credit the receiver wallet. If currencies differ, the snapshotted `exchange_rate` records the rate used (stored on both legs). |
| FX rate | A `(from_currency, to_currency) → rate` entry. Source of truth is the `fx_rate` table (static seed via Flyway, mutable through an admin-only path). Read-through cached in Redis with a TTL. Used at transfer time only; never used to revalue stored balances. |
| Deposit | A one-leg credit to a wallet (simulated funding). |
| Withdraw | A one-leg debit from a wallet (simulated cash-out). |
| Transaction | One **leg** of a wallet movement — one row in the `transaction` table. A deposit and a withdraw produce one row each; a transfer produces two rows (debit + credit) sharing a `transfer_id`. The 4-value API filter (`deposit` / `withdraw` / `transfer_debit` / `transfer_credit`) is derived from `type` × `direction`. |
| Category | A user-facing label attached to a transaction (Food, Entertainment, …) used by PFM. Seeded via Flyway; `category.id` is `int`. |
| Budget | A monthly per-user spending plan, scoped to the user's `base_currency`. UNIQUE per `(user_id, month)`; `month` is `YYYY-MM-01`. |
| Bucket | One row of a budget — `(budget_id, category_id, planned_amount, threshold_percent?)`. The spent amount lives in Redis (hot) + Postgres materialized view (durable backup) per NFR6, not on the `budget_bucket` table. |
| Threshold | A soft warning level on a bucket as an integer percent in `[1, 100]` (e.g. 80% of `planned_amount`). NULL when the user has not set one. |
| Idempotency Key | Client-supplied UUID guaranteeing at-most-once side effects on a mutating endpoint. Tracked in the `idempotency_record` table with `(user_id, endpoint, idempotency_key)` UNIQUE. |
| Outbox | A DB table written in the same transaction as a money mutation; a `@Scheduled` poller drains rows where `published_at IS NULL` to Kafka. |
| Event time | The `event_timestamp` carried in the Kafka payload (NFR7), distinct from the consumer's processing time and from the `created_at` DB-insert column. |
| Velocity | Number of transactions per user per unit time (input to FR2.1). |
| Volume | Cumulative transaction amount per user per unit time (input to FR2.2). |
| Fraud counter | Redis sliding-window counter (per user, per rule — velocity / volume) read by the sync pre-check (NFR9) to decide whether a candidate transaction would breach a threshold. |
| Fraud status | User-level enum (`ACTIVE`, `SUSPENDED`) stored on `user.fraud_status`. `SUSPENDED` users are rejected by the sync money path with error key `account.suspended` (preserved for API back-compat). Set by the async fraud consumer; **manual unsuspend is deferred in MVP.** |
| Fraud block | A money mutation rejected synchronously by the fraud pre-check (NFR9). Persists a `transaction.blocked` outbox event — no ledger row is written. *(MVP: the `audit_log` row originally specified for block paths is deferred — see [../../project-info.md §8](../../project-info.md#8-security-baseline).)* |

See also the database expression of these concepts in [../database/README.md](../database/README.md).

## 3. Typical user journeys

### End user (wallet holder)

The end-user persona acts on the system daily ([../../project-info.md §2.1](../../project-info.md#21-user-personas)). A representative session:

1. **Sign up** at `POST /users` with `{ email, password, base_currency }` (the `base_currency` is immutable once chosen). Then log in at `POST /auth/login` to obtain a JWT.
2. **Open a wallet** in a chosen currency, with a user-supplied `label`, via `POST /users/{userId}/wallets` (FR1.1). The same currency may be opened more than once (e.g. `"Savings USD"` + `"Travel USD"`).
3. **Top up** the wallet via `POST /wallets/{walletId}/deposits` with an `Idempotency-Key` (FR1.2).
4. **Transfer** money to another user via `POST /transfers` (recipient addressed by `to_user_id`), optionally tagging the movement with a `category_id` for PFM. Cross-currency transfers convert at request time using a cached FX rate; the rate is snapshotted onto both legs of the resulting `transaction` rows (FR1.3).
5. **Inspect the statement** via `GET /wallets/{walletId}/transactions`, filtering by date range or transaction type (FR1.4).
6. **Create a monthly budget** at `POST /budgets`, with one bucket per spending category and an optional soft `threshold_percent` (FR4.1, FR4.3). The budget inherits the user's `base_currency` — buckets do not carry a per-row currency.
7. **Receive realtime updates** as transactions land — the PFM consumer drains `transaction-events` and adjusts buckets automatically (FR4.2); buckets that cross their threshold trigger WebSocket notifications (FR5.1).
8. **Read a predictive warning** when current burn rate would exhaust a bucket before month-end (FR5.2).
9. **Ask the advisor for end-of-month analysis** via `POST /advisor/analyze` (HTTP 202; the personalised advice arrives over WebSocket — FR6.1, FR6.2, NFR8). Optionally request an auto-adjusted plan for next month via `POST /advisor/auto-adjust` (FR6.3).

See the matching endpoint rows in [../api/README.md](../api/README.md) and the rule expression in [../business-rules/core-wallet-management-rules.md](../business-rules/core-wallet-management-rules.md) / [../business-rules/ai-driven-personal-finance-management-rules.md](../business-rules/ai-driven-personal-finance-management-rules.md).

### Admin / Ops

This persona operates during business hours ([../../project-info.md §2.1](../../project-info.md#21-user-personas)).

1. **Log in** with a user account that has been granted the `ADMIN` role.
2. **Open the live dashboard** at the frontend admin route, which calls `GET /admin/metrics/live` for first-paint and then subscribes to `WS /admin/ws/metrics` for live daily transaction count and volume (FR3.1).
3. **Receive fraud-alert toasts** via `WS /admin/ws/alerts` without reloading the page (FR3.2). In MVP, admin temporarily covers the analyst read side until the `FRAUD_ANALYST` role ships (see [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md)).
4. **Inspect a flagged user** by drilling into the user record (admin-only read; durable justification via `audit_log` is deferred in MVP — [../../project-info.md §8](../../project-info.md#8-security-baseline)).

See [../business-rules/real-time-admin-dashboard-rules.md](../business-rules/real-time-admin-dashboard-rules.md).

### Fraud analyst — DEFERRED in MVP

A dedicated fraud-analyst persona and the manual unsuspend / alert-resolution workflow are **deferred for MVP** ([../../project-info.md §2.1](../../project-info.md#21-user-personas), [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md)). In MVP, suspensions are automatic-only (FR2.4); admin temporarily reads the alert stream. When manual unsuspend, fraud-rule tuning, or analyst-workflow UI ships, the `FRAUD_ANALYST` role and the `audit_log` table return together via an ADR superseding [../decisions/0009-rbac-roles.md](../decisions/0009-rbac-roles.md) / [../decisions/0010-fraud-enforcement-model.md](../decisions/0010-fraud-enforcement-model.md).

See [../business-rules/fraud-detection-engine-rules.md](../business-rules/fraud-detection-engine-rules.md).

## 4. What this product is NOT

From [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope):

- **Not** a bank: there is no real settlement, no card or KYC onboarding, no real bank-rail integration. Deposits and withdrawals are simulated.
- **Not** a mobile app: MVP is web-only.
- **Not** SSO-federated: no integration with external identity providers in MVP.
- **Not** a live FX market: cross-currency transfers use a static `fx_rate` table seeded via Flyway and mutated only through an admin path. No real-time market rates, no external FX provider.
- **Not** a rule-based advisor fallback: if the LLM circuit is open, the advisor surfaces an "unavailable" state rather than degrading to heuristics.
