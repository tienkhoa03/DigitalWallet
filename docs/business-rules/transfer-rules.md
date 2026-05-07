# Transfer Rules

All paths in **Enforced in** are `(verify)` until backend code lands. Each rule below is derived from [../../README.md FR1.3](../../README.md), [NFR1–NFR3, NFR5](../../README.md), and [§5 transfer data flow](../../README.md).

## Idempotency-Key required

- **Rule**: `POST /transfers` must include an `Idempotency-Key: <uuid>` HTTP header. Missing or malformed → reject.
- **Why**: NFR3 — a spam-clicked button or a network retry must never double-debit.
- **Enforced in**: transfer JAX-RS resource (verify).
- **Failure mode**: `400 Bad Request` with error key `IDEMPOTENCY_KEY_REQUIRED` (verify).
- **Frontend shortcut**: client generates a fresh UUID per submit; reuses it on retries until the server returns 2xx.

## Idempotency-Key replay returns the original outcome

- **Rule**: a repeated key with the same payload returns the original response without re-processing.
- **Why**: NFR3 — at-most-once balance change.
- **Enforced in**: idempotency middleware backed by Redis (verify).
- **Failure mode**: `200 OK` with the cached body — no double charge.
- **Frontend shortcut**: none — server invariant.

## Idempotency-Key conflict on a different payload

- **Rule**: same key, different payload → reject (do not silently replay).
- **Why**: prevents key reuse from masking real client bugs.
- **Enforced in**: idempotency middleware (verify).
- **Failure mode**: `409 Conflict` with error key `IDEMPOTENCY_KEY_CONFLICT`.
- **Frontend shortcut**: none.

## Pessimistic lock on both wallets

- **Rule**: sender and receiver wallet rows are locked with `SELECT … FOR UPDATE` (or a Redisson distributed lock) before reading balance.
- **Why**: NFR1 — concurrent transfers can otherwise read the same balance and both pass.
- **Enforced in**: transfer service (verify) using `LockModeType.PESSIMISTIC_WRITE`.
- **Failure mode**: blocking wait, then proceed; lock-acquisition timeout → `503` or domain-specific 4xx (verify).
- **Frontend shortcut**: none.

## Lock acquisition order

- **Rule**: locks on the two wallets must be acquired in a deterministic order (e.g., by wallet ID ascending).
- **Why**: two transfers `A→B` and `B→A` running concurrently will deadlock if each locks its sender first. *(Spec implies this; not stated explicitly.)*
- **Enforced in**: transfer service (verify).
- **Failure mode**: deadlock → DB rollback → client retries with the same `Idempotency-Key`.
- **Frontend shortcut**: none.

## Sender must have sufficient funds

- **Rule**: post-debit balance must be ≥ 0.
- **Why**: NFR2 — money cannot be created.
- **Enforced in**: service guard inside the locked transaction (verify); DB-level `CHECK (balance >= 0)` (verify).
- **Failure mode**: `409 Conflict` with error key `INSUFFICIENT_FUNDS` (verify).
- **Frontend shortcut**: client may grey out the submit button when input > displayed balance.

## Sender ≠ receiver

- **Rule**: `from_wallet != to_wallet`.
- **Why**: a self-transfer is a no-op that pollutes history and triggers velocity false positives.
- **Enforced in**: DTO validator (verify).
- **Failure mode**: `400 Bad Request` with error key `SAME_WALLET`.
- **Frontend shortcut**: client-side same-wallet check.

## Amount > 0 and within precision

- **Rule**: `amount > 0`, fits `numeric(19,4)` (verify).
- **Why**: NFR2 — negative amounts would be a back-door deposit.
- **Enforced in**: DTO validator (verify).
- **Failure mode**: `400 Bad Request`.
- **Frontend shortcut**: numeric input constraints.

## Commit-then-publish ordering

- **Rule**: the request thread (a) commits the DB transaction, then (b) publishes to `transaction-events`. The `200 OK` is returned only after the publish (or outbox write) succeeds.
- **Why**: NFR2 — the DB and Kafka must not silently diverge.
- **Enforced in**: transfer service (verify); strategy detail in [../decisions/0006-outbox-or-publish-after-commit.md](../decisions/0006-outbox-or-publish-after-commit.md).
- **Failure mode**: a publish failure after commit must surface as 5xx so the client can resolve via idempotent retry.
- **Frontend shortcut**: none.

## Fraud detection runs only on the consumer thread

- **Rule**: the request thread does no risk analysis.
- **Why**: NFR5 — risk computation must not add latency or failure modes to transfer.
- **Enforced in**: fraud module is a Kafka listener (verify); never invoked from `wallet/`, `transaction/`, or any JAX-RS resource.
- **Failure mode**: rule misfires affect only the alert stream, never the transfer outcome.
- **Frontend shortcut**: none.
