# 0006 — Multi-currency model

## Status

Accepted

## Date

2026-05-21

## Deciders

TBD

## Context

DigitalWallet supports multi-currency wallets (FR1.1, FR1.3 in [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs)). Two shapes are common: one wallet per currency (cheap balances; FX applied per-transfer) or one wallet with a balance dictionary (single account abstraction; FX applied at read time). Live FX is explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)). The team needed an ADR capturing which shape is preferred, whether a `(account_id, currency_code)` uniqueness constraint should hold, and where the FX rate is sourced. Source: [../../project-info.md §10 row 6](../../project-info.md#10-open-architectural-decisions-adrs-to-write).

A follow-up question was raised on 2026-05-21: must each account hold at most one wallet per currency, or should an account be allowed to open several wallets in the same currency (e.g. a "Savings USD" wallet alongside a "Travel USD" wallet)? Several end-user personas in [../../project-info.md §2.1](../../project-info.md#21-user-personas) routinely segregate funds by purpose, and forcing a single wallet per currency pushes that segregation either into a second account (heavy) or into client-side bookkeeping (lossy).

## Options considered

- **Multiple wallets per account, each scoped to a single currency; an account MAY own several wallets in the same currency, disambiguated by an account-supplied `label`** — explicit per-wallet balances; FX only impacts the cross-currency transfer leg; users can model "Savings USD" vs. "Travel USD" without opening a second account.
- **One wallet per currency per account** — simpler aggregation, single row to lock per currency; but forces users who want segregation to open multiple accounts, which fragments PFM and audit-log views.
- **Single wallet, multi-currency balance dictionary** — fewer rows but every read becomes a conversion; harder to audit per-currency state; does not address the segregation use case at all.
- **External FX provider with live rates** — explicitly out of scope ([../../project-info.md §11](../../project-info.md#11-explicit-non-goals-out-of-scope)).

## Decision

**Multiple wallets per account, each scoped to a single currency; an account MAY own several wallets in the same currency, disambiguated by an account-supplied `label`. Cross-currency transfers convert the sender debit using the cached FX rate for `(from_currency, to_currency)`; the same rate is never used to revalue stored balances.**

Concretely:

- The `wallet` table carries `(id, account_id, currency_code, label, balance, …)` with `UNIQUE (account_id, label)` and **no** `UNIQUE (account_id, currency_code)`. See [../database/README.md](../database/README.md) `wallet`.
- Every wallet remains scoped to a single currency — the table is not a balance dictionary.
- The Redis distributed lock per NFR1 is keyed on `wallet_id`, which is stable regardless of how many sibling wallets in the same currency the account holds; siblings do not contend on the same lock key.
- PFM aggregation (FR4.x) groups spending by `(account_id, category, month_of(transaction_timestamp))` across **all** wallets the account owns. The PFM consumer does not assume a one-wallet-per-currency mapping.
- The cross-currency FX leg uses the cached `fx_rate` for the sender's wallet currency → receiver's wallet currency. The `fx_rates` table is a static seed via Flyway, mutable through an admin-only path, read-through cached in Redis with TTL `FX_RATE_TTL_SECONDS` ([../../project-info.md §9](../../project-info.md#9-domain-glossary), [../../project-info.md §14](../../project-info.md#14-environment--configuration)).

## Consequences

- **Easier:** end users can segregate funds by purpose without opening multiple accounts; transfer endpoints stay simple because every wallet has a single currency; FX continues to be a per-event calculation; audit log and SOC 2 trail remain centred on one account identity.
- **Harder:** the UI MUST surface wallet `label` everywhere a wallet is selectable (deposit, withdraw, transfer source); the wallet-opening form MUST ask for a label and reject duplicates within the same account; statement and balance-summary views need to render label alongside currency to avoid ambiguity.
- **Live with:** PFM aggregation queries cross-join across more wallets per account on average; the cost is bounded because the wallet count per account remains small (a handful, not hundreds), and the join is keyed on `account_id` which is already indexed.
- **Revisit if:** product later wants to enforce a one-wallet-per-currency invariant for a regulated jurisdiction (would require adding `UNIQUE (account_id, currency_code)` plus a migration that merges existing siblings, both forward-only Flyway steps).

## References

- [../../project-info.md §5 FR1.1](../../project-info.md#5-functional-requirements-epics--frs) — wallet-opening rule (multiple wallets per currency allowed).
- [../../project-info.md §9](../../project-info.md#9-domain-glossary) — Wallet glossary entry.
- [../business-rules/core-wallet-management-rules.md](../business-rules/core-wallet-management-rules.md) — FR1.1 enforcement.
- [../database/README.md](../database/README.md) — `wallet` schema.
