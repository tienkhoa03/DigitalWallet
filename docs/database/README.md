# Database

- **Engine (prod & test)**: PostgreSQL — version (verify; pin in `docker-compose.yml` once committed).
- **Schema management**: Flyway, forward-only migrations under (path verify, conventionally `backend/src/main/resources/db/migration/`).
- **Time-series sink (optional)**: InfluxDB for fraud-alert history and transaction metrics — see [../../README.md §2](../../README.md).

> **Status:** no migrations have been written yet — the table list below is **derived from the spec** ([../../README.md §3, §5](../../README.md), [NFR2, NFR3](../../README.md)) and is **(spec — not yet implemented)**. Replace each row with a link to the migration that creates it once committed. See [migrations.md](migrations.md) for the (currently empty) migration log.

## §1 ERD (spec — not yet implemented)

```
┌──────────────────┐         ┌──────────────────────┐
│ accounts         │ 1     n │ wallets              │
│ ──────────────── │ ──────▶ │ ──────────────────── │
│ PK id            │         │ PK id                │
│ UQ identifier    │         │ FK account_id        │
│    created_at    │         │    balance           │  -- locked SELECT FOR UPDATE
└──────────────────┘         │    currency? (verify)│
                             │    created_at        │
                             └────────┬─────────────┘
                                      │ 1
                                      │
                                      │ n
                             ┌────────▼─────────────┐
                             │ transaction_history  │   -- two rows per transfer
                             │ ──────────────────── │      (debit + credit)
                             │ PK id                │
                             │ FK wallet_id         │
                             │    counterparty_id   │
                             │    type              │   -- DEPOSIT/WITHDRAW/TRANSFER
                             │    direction         │   -- DEBIT/CREDIT
                             │    amount            │
                             │    idempotency_key   │
                             │    created_at        │
                             └──────────────────────┘

┌──────────────────────┐     ┌────────────────────────┐
│ idempotency_keys     │     │ outbox_events          │
│  (Redis OR table —   │     │  (only if Outbox       │
│   see ADR 0005)      │     │   Pattern adopted —    │
│ ──────────────────── │     │   see ADR 0006)        │
│ PK key (uuid)        │     │ ────────────────────── │
│    request_hash      │     │ PK id                  │
│    response_status   │     │    aggregate_id        │
│    response_body     │     │    topic               │
│    created_at        │     │    payload             │
│    expires_at        │     │    published_at NULL   │
└──────────────────────┘     │    created_at          │
                             └────────────────────────┘
```

## §2 Table overview (spec — not yet implemented)

| Table | Purpose | Source rule |
|---|---|---|
| `accounts` | User identities. | FR1.1 |
| `wallets` | Per-account balance store; rows are pessimistically locked during transfers. | FR1.1, NFR1 |
| `transaction_history` | Append-only log of debits/credits — two rows per transfer per [../../README.md §5](../../README.md). | FR1.4 |
| `idempotency_keys` | Records previously-processed `Idempotency-Key` headers. May live in Redis only — see [../decisions/0005-idempotency-key-header.md](../decisions/0005-idempotency-key-header.md). | NFR3 |
| `outbox_events` | Buffered events bridging DB commit and Kafka publish. Optional — depends on [../decisions/0006-outbox-or-publish-after-commit.md](../decisions/0006-outbox-or-publish-after-commit.md). | NFR2 |

## §3 Conventions (proposed — verify when migrations land)

- IDs: choice deferred (UUID v4, ULID, or `bigserial`) — open.
- Timestamps: `timestamptz`, UTC, `created_at` non-null.
- Money: `numeric(19,4)` for `balance` and `amount` columns (verify).
- No soft delete — financial records are append-only.
- Every balance-mutating SQL path uses `SELECT … FOR UPDATE` (NFR1).
