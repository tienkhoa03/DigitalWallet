# Wallet Rules

Derived from [../../README.md FR1.1, FR1.2](../../README.md). All `Enforced in` paths are `(verify)`.

## Account creation

- **Rule**: an account is created with a unique identifier (email or login — spec is silent, verify).
- **Why**: a wallet must belong to exactly one identity (FR1.1).
- **Enforced in**: account JAX-RS resource (verify); unique constraint on the identifier column (verify).
- **Failure mode**: `409 Conflict` if the identifier already exists.
- **Frontend shortcut**: none.

## Wallet ownership

- **Rule**: a wallet is owned by exactly one account (FK `wallets.account_id`).
- **Why**: ownership is the basis of authorization on every wallet operation.
- **Enforced in**: persistence layer FK (verify).
- **Failure mode**: `404` for a non-existent account; FK violation otherwise.
- **Frontend shortcut**: none.

## Deposit amount > 0

- **Rule**: `POST /wallets/{id}/deposit` requires `amount > 0`.
- **Why**: a non-positive deposit is either a no-op or a back-door withdrawal.
- **Enforced in**: DTO validator (verify).
- **Failure mode**: `400 Bad Request`.
- **Frontend shortcut**: numeric input lower bound.

## Withdrawal does not overdraw

- **Rule**: `POST /wallets/{id}/withdraw` succeeds only if `balance - amount >= 0`, evaluated under a pessimistic lock.
- **Why**: NFR1 + NFR2 — money cannot be created.
- **Enforced in**: wallet service (verify) under `LockModeType.PESSIMISTIC_WRITE`; DB `CHECK (balance >= 0)` (verify).
- **Failure mode**: `409 Conflict` with `INSUFFICIENT_FUNDS`.
- **Frontend shortcut**: client greys out submit when input > displayed balance.

## Balance-mutating operations are transactional

- **Rule**: every deposit / withdrawal / transfer leg runs inside `@Transactional` (Narayana JTA via Quarkus).
- **Why**: NFR2.
- **Enforced in**: service-layer annotations (verify).
- **Failure mode**: any exception → full rollback.
- **Frontend shortcut**: none.

## Transaction history is append-only

- **Rule**: rows in `transaction_history` are inserted, never updated or deleted.
- **Why**: history is the audit trail and the substrate for FR1.4 + the fraud rules.
- **Enforced in**: by convention in the persistence layer (verify); enforce with a DB rule or a revoked `UPDATE/DELETE` privilege if escalating (open).
- **Failure mode**: any update path is a bug, not a runtime error.
- **Frontend shortcut**: none.
