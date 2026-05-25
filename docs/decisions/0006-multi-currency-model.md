# 0006 — Multi-currency model

## Status

Accepted

## Date

2026-05-21

## Deciders

TBD

## Context

DigitalWallet supports multi-currency wallets (FR1.1, FR1.3 in [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs)). Two shapes are common: one wallet per currency (cheap balances; FX applied per-transfer) or one wallet with a balance dictionary (single user abstraction; FX applied at read time). Live FX is explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)). The team needed an ADR capturing which shape is preferred, whether a `(user_id, currency)` uniqueness constraint should hold, and where the FX rate is sourced. Source: [../../project-info.md §10 row 6](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

A follow-up question was raised on 2026-05-21: must each user hold at most one wallet per currency, or should a user be allowed to open several wallets in the same currency (e.g. a "Savings USD" wallet alongside a "Travel USD" wallet)? Several end-user personas in [../../project-info.md §2.1](../../project-info.md#21-user-personas) routinely segregate funds by purpose, and forcing a single wallet per currency pushes that segregation either into a second user account (heavy) or into client-side bookkeeping (lossy).

A second follow-up on 2026-05-25 added the **immutable `base_currency`** column on the `user` row. Budgets are denominated in this single currency; PFM converts cross-currency spending back to the user's `base_currency` using the FX rate **snapshotted** on the `transaction` row at commit time. This isolates budget reporting from live FX drift and from the runtime cache TTL.

## Options considered

- **Multiple wallets per user, each scoped to a single currency; a user MAY own several wallets in the same currency, disambiguated by a user-supplied `label`** — explicit per-wallet balances; FX only impacts the cross-currency transfer leg; users can model "Savings USD" vs. "Travel USD" without opening a second user.
- **One wallet per currency per user** — simpler aggregation, single row to lock per currency; but forces users who want segregation to open multiple accounts, which fragments PFM views.
- **Single wallet, multi-currency balance dictionary** — fewer rows but every read becomes a conversion; harder to audit per-currency state; does not address the segregation use case at all.
- **External FX provider with live rates** — explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)).

## Decision

**Multiple wallets per user, each scoped to a single currency; a user MAY own several wallets in the same currency, disambiguated by a user-supplied `label`. Cross-currency transfers convert the sender debit using the cached FX rate for `(from_currency, to_currency)`; the chosen rate is snapshotted onto both legs of the resulting `transaction` rows (`exchange_rate` column). Stored balances are never revalued.**

**Each user carries an immutable `user.base_currency` chosen at signup. Budgets are scoped to it; the PFM consumer converts cross-currency spending back to the `base_currency` using the snapshotted `transaction.exchange_rate` — never a live lookup.**

Concretely:

- The `wallet` table carries `(id, user_id, currency, label, balance, …)` with `UNIQUE (user_id, label)` and **no** `UNIQUE (user_id, currency)`. See [../database/README.md](../database/README.md) `wallet`.
- Every wallet remains scoped to a single currency — the table is not a balance dictionary.
- The Redis distributed lock per NFR1 is keyed on `wallet_id`, which is stable regardless of how many sibling wallets in the same currency the user holds; siblings do not contend on the same lock key.
- PFM aggregation (FR4.x) groups spending by `(user_id, category_id, month_of(event_timestamp))` across **all** wallets the user owns, with cross-currency amounts converted to `user.base_currency` via the snapshotted `transaction.exchange_rate`. The PFM consumer does not assume a one-wallet-per-currency mapping.
- The cross-currency FX leg uses the cached `fx_rate` for the sender's wallet currency → receiver's wallet currency. The `fx_rate` table is a static seed via Flyway, mutable through an admin-only path, read-through cached in Redis with TTL `FX_RATE_TTL_SECONDS` ([../../project-info.md §9](../../project-info.md#9-domain-glossary), [../../project-info.md §14](../../project-info.md#14-environment--configuration)).

## Consequences

- **Easier:** end users can segregate funds by purpose without opening multiple users; transfer endpoints stay simple because every wallet has a single currency; FX continues to be a per-event calculation; PFM budget rollup has a single, immutable denominator per user.
- **Harder:** the UI MUST surface wallet `label` everywhere a wallet is selectable (deposit, withdraw, transfer source); the wallet-opening form MUST ask for a label and reject duplicates within the same user; statement and balance-summary views need to render label alongside currency to avoid ambiguity. The `base_currency` choice at signup is one-way — no in-MVP change path.
- **Live with:** PFM aggregation queries cross-join across more wallets per user on average; the cost is bounded because the wallet count per user remains small (a handful, not hundreds), and the join is keyed on `user_id` which is already indexed. `transaction.exchange_rate` is populated on both legs of every cross-currency transfer (a small redundancy that simplifies PFM reads).
- **Revisit if:** product later wants to enforce a one-wallet-per-currency invariant for a regulated jurisdiction (would require adding `UNIQUE (user_id, currency)` plus a migration that merges existing siblings, both forward-only Flyway steps); or product needs to let users change their `base_currency` (would require a budget-revaluation migration).

## References

- [../../project-info.md §5 FR1.1](../../project-info.md#5-functional-requirements-epics--frs) — wallet-opening rule (multiple wallets per currency allowed).
- [../../project-info.md §9](../../project-info.md#9-domain-glossary) — Wallet glossary entry.
- [../business-rules/core-wallet-management-rules.md](../business-rules/core-wallet-management-rules.md) — FR1.1 enforcement.
- [../database/README.md](../database/README.md) — `wallet` schema.
