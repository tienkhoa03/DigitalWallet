# Domain knowledge

This page is the product-side framing of DigitalWallet — what the system is, who uses it, and the vocabulary it commits to.

## 1. What the product is

DigitalWallet is a multi-currency internal wallet platform with real-time fraud detection and an AI-driven personal finance manager (PFM) that learns from each user's spending stream ([../../project-info.md §1](../../project-info.md#1-project-identity)). The primary value the project sets out to prove is that a single event-driven backbone can serve both a strict ACID money ledger **and** a derived, AI-augmented analytics stream without coupling them on the request thread. The product addresses three audiences in parallel: end users who deposit, withdraw, transfer, and want guidance on their own spending; operations admins who need a live view of transaction volume; and fraud analysts who triage alerts surfaced by the engine ([../../project-info.md §2.1](../../project-info.md#21-user-personas)).

## 2. Core domain concepts

Source: [../../project-info.md §9](../../project-info.md#9-domain-glossary).

| Term | Meaning in this product |
|---|---|
| Account | A user identity; owns one or more wallets. |
| Wallet | A balance-bearing record owned by an account, scoped to a single currency. |
| Transfer | A two-leg atomic operation: debit the sender wallet, credit the receiver wallet. If currencies differ, an FX leg converts the debit amount using a cached rate. |
| FX rate | A `(from_currency, to_currency) → rate` entry. Source of truth is the `fx_rates` table (static seed via Flyway, mutable through an admin-only path). Read-through cached in Redis with a TTL. Used at transfer time only; never used to revalue stored balances. |
| Deposit | A one-leg credit to a wallet (simulated funding). |
| Withdraw | A one-leg debit from a wallet (simulated cash-out). |
| Transaction | The umbrella term for any wallet movement (deposit, withdraw, or one leg of a transfer). |
| Category | A user-facing label attached to a transaction (Food, Entertainment, …) used by PFM. |
| Budget | A monthly per-category spending plan owned by a user. |
| Bucket | One row of a budget — `(user, month, category, planned_amount, spent_amount)`. |
| Threshold | A soft warning level on a bucket (e.g. 80% of `planned_amount`). |
| Idempotency Key | Client-supplied UUID guaranteeing at-most-once side effects on a mutating endpoint. |
| Outbox | A DB table written in the same transaction as a money mutation; a poller publishes its rows to Kafka. |
| Event time | The `transaction_timestamp` carried in the Kafka payload (NFR7), distinct from the consumer's processing time. |
| Velocity | Number of transactions per account per unit time (input to FR2.1). |
| Volume | Cumulative transaction amount per account per unit time (input to FR2.2). |

See also the database expression of these concepts in [../database/README.md](../database/README.md).

## 3. Typical user journeys

### End user (wallet holder)

The end-user persona acts on the system daily ([../../project-info.md §2.1](../../project-info.md#21-user-personas)). A representative session:

1. **Sign up** at `POST /accounts`, then log in at `POST /auth/login` to obtain a JWT.
2. **Open a wallet** in a chosen currency via `POST /accounts/{accountId}/wallets` (FR1.1).
3. **Top up** the wallet via `POST /wallets/{walletId}/deposits` with an `Idempotency-Key` (FR1.2).
4. **Transfer** money to another user via `POST /transfers`, optionally tagging the movement with a `category_id` for PFM. Cross-currency transfers convert at request time using a cached FX rate (FR1.3).
5. **Inspect the statement** via `GET /wallets/{walletId}/transactions`, filtering by date range or transaction type (FR1.4).
6. **Create a monthly budget** at `POST /budgets`, with one bucket per spending category and an optional soft `threshold_percent` (FR4.1, FR4.3).
7. **Receive realtime updates** as transactions land — the PFM consumer drains `transaction-events` and adjusts buckets automatically (FR4.2); buckets that cross their threshold trigger WebSocket notifications (FR5.1).
8. **Read a predictive warning** when current burn rate would exhaust a bucket before month-end (FR5.2).
9. **Ask the advisor for end-of-month analysis** via `POST /advisor/analyze` (HTTP 202; the personalised advice arrives over WebSocket — FR6.1, FR6.2, NFR8). Optionally request an auto-adjusted plan for next month via `POST /advisor/auto-adjust` (FR6.3).

See the matching endpoint rows in [../api/README.md](../api/README.md) and the rule expression in [../business-rules/core-wallet-management-rules.md](../business-rules/core-wallet-management-rules.md) / [../business-rules/ai-driven-personal-finance-management-rules.md](../business-rules/ai-driven-personal-finance-management-rules.md).

### Admin / Ops

This persona operates during business hours ([../../project-info.md §2.1](../../project-info.md#21-user-personas)).

1. **Log in** with an account that has been granted the `ADMIN` role.
2. **Open the live dashboard** at the frontend admin route, which calls `GET /admin/metrics/live` for first-paint and then subscribes to `WS /admin/ws/metrics` for live daily transaction count and volume (FR3.1).
3. **Receive fraud-alert toasts** via `WS /admin/ws/alerts` without reloading the page (FR3.2).
4. **Inspect a flagged account** by drilling into the user record (admin-only read, audited per SOC 2 — [../../project-info.md §8](../../project-info.md#8-security-baseline)).

See [../business-rules/real-time-admin-dashboard-rules.md](../business-rules/real-time-admin-dashboard-rules.md).

### Fraud analyst

This persona acts occasionally and is kept separate from `ADMIN` to enforce least privilege ([../../project-info.md §2.2](../../project-info.md#22-roles-in-the-system)).

1. **Log in** with an account granted the `FRAUD_ANALYST` role.
2. **Subscribe to the fraud-alert stream** (`WS /admin/ws/alerts`) and **list historical alerts** via `GET /fraud/alerts`.
3. **Investigate evidence** attached to each alert (window, counts, sums recorded by the fraud engine — FR2.1, FR2.2).
4. **Tune thresholds** by changing the corresponding env vars (`FRAUD_VELOCITY_*`, `FRAUD_VOLUME_*` in [../architecture/README.md#7-config--profiles](../architecture/README.md#7-config--profiles)).
5. **Mark an alert as confirmed or false-positive** via `POST /fraud/alerts/{alertId}/resolution` `(verify — endpoint derived, not explicit in §5)`.

See [../business-rules/fraud-detection-engine-rules.md](../business-rules/fraud-detection-engine-rules.md).

## 4. What this product is NOT

From [../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope):

- **Not** a bank: there is no real settlement, no card or KYC onboarding, no real bank-rail integration. Deposits and withdrawals are simulated.
- **Not** a mobile app: MVP is web-only.
- **Not** SSO-federated: no integration with external identity providers in MVP.
- **Not** a live FX market: cross-currency transfers use a static `fx_rates` table seeded via Flyway and mutated only through an admin path. No real-time market rates, no external FX provider.
- **Not** a rule-based advisor fallback: if the LLM circuit is open, the advisor surfaces an "unavailable" state rather than degrading to heuristics.
