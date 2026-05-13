# Epic 1 — Core Wallet Management rules

This page captures the per-FR rules for Epic 1 (FR1.1–FR1.4) from [../../project-info.md §5](../../project-info.md#5-functional-requirements-epics--frs). Endpoint shapes are in [../api/README.md](../api/README.md); table definitions in [../database/README.md](../database/README.md).

## FR1.1 — Account creation and wallet opening

- **Rule:** A user may hold exactly one wallet per ISO 4217 currency under a single account; opening a second wallet in the same currency is rejected.
- **Why:** Required by the multi-currency model in [../decisions/0006-multi-currency-model.md](../decisions/0006-multi-currency-model.md) — every wallet is scoped to one currency, and PFM accounting assumes a single wallet per (account, currency) pair.
- **Enforced in:** `wallet/service/` opening flow, backed by a `UNIQUE (account_id, currency_code)` DB constraint in [../database/README.md](../database/README.md). `(verify)`
- **Failure mode:** HTTP 409 with `error_key: "wallet.duplicate_currency"`. DB constraint violation surfaces the same key through the exception mapper.
- **Frontend shortcut:** The wallet-creation form filters out currencies already owned by the account (cosmetic — the server invariant remains).

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

- **Rule (FX at transfer time only):** Cross-currency transfers convert the sender debit using the cached FX rate for `(from_currency, to_currency)`; the same rate is **never** used to revalue stored balances.
- **Why:** [../decisions/0006-multi-currency-model.md](../decisions/0006-multi-currency-model.md) — one wallet per currency; FX is a per-event calculation, not a balance-revaluation policy.
- **Enforced in:** `wallet/service/` FX leg; FX cache in `shared/`. `(verify)`
- **Failure mode:** Missing rate → HTTP 422 `error_key: "transfer.fx_rate_missing"`.
- **Frontend shortcut:** The transfer form previews the converted amount using the same cached rate; the preview is informational and may diverge from the committed rate if the TTL expires between preview and submit.

- **Rule (rate limit):** `POST /transfers` is limited to 10 requests per minute per user via the Redis token bucket.
- **Why:** [../../project-info.md §8](../../project-info.md#8-security-baseline) — cost control plus a defence-in-depth layer against credential stuffing or spam-click DoS.
- **Enforced in:** Rate-limit middleware in `shared/`. `(verify)`
- **Failure mode:** HTTP 429 `error_key: "ratelimit.exceeded"` with `Retry-After`.
- **Frontend shortcut:** Submit buttons disable for ~6 s after a successful transfer (cosmetic).

- **Rule (audit):** Every committed transfer writes a row to `audit_log` with `action = "transfer.commit"` and a redacted payload.
- **Why:** SOC 2 audit obligation ([../../project-info.md §8](../../project-info.md#8-security-baseline)).
- **Enforced in:** `wallet/service/` transfer service. `(verify)`
- **Failure mode:** Audit-log failure aborts the transaction (HTTP 500 `error_key: "audit.write_failed"`); money cannot move silently.
- **Frontend shortcut:** None.

## FR1.4 — Transaction history (statement)

- **Rule:** Statements are read from the ledger filtered by `wallet_id`, optional time range, and optional `type` (`deposit`, `withdraw`, `transfer_debit`, `transfer_credit`). Reads never block writes (no `FOR UPDATE`).
- **Why:** FR1.4; NFR1 reserves write locks for mutations only.
- **Enforced in:** `wallet/persistence/` query; `wallet/api/` resource. `(verify)`
- **Failure mode:**
  - Reverse time range → HTTP 400 `error_key: "validation.invalid_range"`.
  - Requesting another user's wallet → HTTP 403 `error_key: "auth.forbidden"`; `ADMIN` may bypass via the admin path with an `audit_log` entry.
- **Frontend shortcut:** Default range is "last 30 days"; date pickers clamp to the wallet's `opened_at`.
