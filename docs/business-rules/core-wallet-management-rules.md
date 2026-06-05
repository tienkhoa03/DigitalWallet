# Epic 1 — Core Wallet Management rules

This page captures the per-FR rules for Epic 1 (FR1.1–FR1.4) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs). Endpoint shapes are in [../api/README.md](../api/README.md); table definitions in [../database/README.md](../database/README.md).

## FR1.1 — User signup and wallet opening

- **Rule:** Signup creates an `account` row with an **immutable** `base_currency` chosen at sign-up time; every budget owned by the account is implicitly scoped to that currency. An account MAY open multiple wallets, including more than one wallet in the same ISO 4217 currency (e.g. a "Savings USD" wallet alongside a "Travel USD" wallet). Each wallet has its own `wallet_id`, its own balance, and a user-supplied `label` used to disambiguate sibling wallets in the same currency.
- **Why:** Required by the multi-currency model in [../decisions/0006-multi-currency-model.md](../decisions/0006-multi-currency-model.md) — each wallet remains scoped to a single currency, but users routinely want to segregate funds (saving vs. spending, per-trip budgets, etc.) without opening a second account. PFM accounting aggregates across all wallets an account owns; it does not assume one wallet per (account, currency).
- **Enforced in:** `account/service/` signup flow validates `base_currency` against ISO 4217 and persists it as immutable. `wallet/service/` opening flow — there is **no** `UNIQUE (account_id, currency)` constraint. The service validates `currency_code` against ISO 4217, validates `label` is non-empty and unique among that user's wallets (so the label can disambiguate siblings in the UI), and persists with the user-supplied label.
- **Failure mode:**
  - Unsupported currency → HTTP 422 `error_key: "wallet.unsupported_currency"`.
  - Missing or empty `label` → HTTP 400 `error_key: "validation.invalid_payload"`.
  - Duplicate `label` on the same user → HTTP 409 `error_key: "wallet.duplicate_label"`.
- **Frontend shortcut:** None — the wallet-creation form lists every supported currency unconditionally and asks the user for a `label`. (The previous "filter out currencies already owned" shortcut is removed; siblings in the same currency are now a first-class case.)

## FR1.2 — Deposit and withdraw

- **Rule:** Deposit and withdraw mutate the authoritative ledger under the hybrid concurrency protocol — outer Redis lock on `wallet_id` (NFR1), inner DB transaction with `LockModeType.PESSIMISTIC_WRITE` on the wallet row, and an outbox row written in the same transaction (NFR2).
- **Why:** NFR1 (no race on the ledger) + NFR2 (no event lost relative to DB state) + NFR3 (retry safety on flaky networks).
- **Enforced in:** `wallet/service/` deposit and withdraw services; the lock helper in `shared/`; the outbox writer in `shared/`. `(verify)`
- **Failure mode:**
  - Insufficient balance on withdraw → HTTP 422 `error_key: "wallet.insufficient_funds"`.
  - Missing `Idempotency-Key` → HTTP 400 `error_key: "idempotency.key_required"`.
  - Replay with mismatched body → HTTP 409 `error_key: "idempotency.replay_conflict"`.
  - Redis lock not acquired within TTL → HTTP 409 `error_key: "wallet.locked"` (caller may retry).
- **Frontend shortcut:** The deposit/withdraw form generates a UUIDv7 client-side and sends it as `Idempotency-Key`; resubmits reuse the same key to opt into replay-safe behaviour.

## FR1.3 — P2P transfer (including cross-currency)

- **Rule (atomicity):** A transfer is a single ACID transaction containing the sender debit, receiver credit, optional FX conversion, and outbox row. Either every leg commits, or none does.
- **Why:** Money cannot be created or destroyed (NFR2). Cross-system consistency is provided by the Transactional Outbox Pattern.
- **Enforced in:** `wallet/service/` transfer service; outbox writer in `shared/`. `(verify)`
- **Failure mode:** Any leg failure rolls back the whole transaction; HTTP 422 with the leg-specific `error_key` (`transfer.recipient_not_found`, `wallet.insufficient_funds`, `transfer.fx_rate_missing`, `transfer.same_wallet`, …).
- **Frontend shortcut:** None — server invariant.

- **Rule (FX at transfer time only):** Cross-currency transfers convert the sender debit using the cached FX rate for `(from_currency, to_currency)`; the same rate is **never** used to revalue stored balances. The chosen rate is snapshotted onto both legs of the resulting `transaction` rows in the `exchange_rate` column (see [../database/README.md](../database/README.md) `transaction`).
- **Why:** [../decisions/0006-multi-currency-model.md](../decisions/0006-multi-currency-model.md) — every wallet is scoped to a single currency (even when an account owns several wallets in that currency); FX is a per-event calculation on the cross-currency leg, not a balance-revaluation policy. PFM uses the snapshotted `exchange_rate` to convert cross-currency spending back to the account's `base_currency` for budgeting.
- **Enforced in:** `wallet/service/` FX leg; FX cache in `shared/`. `(verify)`
- **Failure mode:** Missing rate → HTTP 422 `error_key: "transfer.fx_rate_missing"`.
- **Frontend shortcut:** The transfer form previews the converted amount using the same cached rate; the preview is informational and may diverge from the committed rate if the TTL expires between preview and submit.

- **Rule (rate limit):** `POST /transfers` is limited to 10 requests per minute per account via the Redis token bucket.
- **Why:** [../../project-info.md §8](../../project-info.md#8-security-baseline) — cost control plus a defence-in-depth layer against credential stuffing or spam-click DoS.
- **Enforced in:** Rate-limit middleware in `shared/`. `(verify)`
- **Failure mode:** HTTP 429 `error_key: "ratelimit.exceeded"` with `Retry-After`.
- **Frontend shortcut:** Submit buttons disable for ~6 s after a successful transfer (cosmetic).

- **Rule (transfer recipient):** The recipient is addressed by `to_account_id` (no `account_number` in MVP — single identifier per account). The receiving wallet is chosen server-side from the recipient's wallets in the requested `currency_code`; ambiguity (recipient owns multiple wallets in that currency) is resolved by the receiver's most-recently-updated matching wallet `(verify)`.
- **Why:** Minimises the external surface area of the transfer payload; preserves the multi-wallet-per-currency model on the receive side without exposing internal wallet ids to senders.
- **Enforced in:** `wallet/service/` transfer service — recipient lookup. `(verify)`
- **Failure mode:** Recipient not found → HTTP 422 `error_key: "transfer.recipient_not_found"`.
- **Frontend shortcut:** Transfer form takes a recipient `account_id`; the wallet picker is presented on the sender side only.

> *MVP scope cut:* the original FR1.3 audit-log rule (`audit_log` row with `action = "transfer.commit"`) is **deferred** along with the `audit_log` table — see [../../project-info.md §8](../../project-info.md#8-security-baseline) and ADR #9. SOC 2 audit obligations return when the table ships.

## FR1.4 — Transaction history (statement)

- **Rule:** Statements are read from the ledger filtered by `wallet_id`, optional time range, and optional `type` (`deposit`, `withdraw`, `transfer_debit`, `transfer_credit`). The 4-value filter is derived from the row's `type` × `direction` columns (see [../database/README.md](../database/README.md) `transaction`). Reads never block writes (no `FOR UPDATE`).
- **Why:** FR1.4; NFR1 reserves write locks for mutations only.
- **Enforced in:** `wallet/persistence/` query; `wallet/api/` resource. `(verify)`
- **Failure mode:**
  - Reverse time range → HTTP 400 `error_key: "validation.invalid_range"`.
  - Requesting another user's wallet → HTTP 403 `error_key: "auth.forbidden"`; `ADMIN` may bypass via the admin path. *(MVP defers the `audit_log` entry — see ADR #9.)*
- **Frontend shortcut:** Default range is "last 30 days"; date pickers clamp to the wallet's `opened_at`.
