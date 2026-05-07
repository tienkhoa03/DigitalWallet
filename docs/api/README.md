# API Reference

> **Status:** every entry is **(spec — not yet implemented)**, derived from [../../README.md §3](../../README.md). Paths and payloads are best-effort inferences from the functional requirements; replace each `(verify)` with a concrete file path once the JAX-RS resources are written.

- **Base URL**: (verify)
- **Auth header**: (unspecified by spec)
- **Idempotency**: state-changing transfer endpoints require `Idempotency-Key: <uuid>` per [NFR3](../../README.md). See [../business-rules/transfer-rules.md](../business-rules/transfer-rules.md).
- **OpenAPI**: none yet — generate one from JAX-RS annotations once they exist.

## Wallet — `/wallets`  *(spec — not yet implemented)*

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/accounts` | (verify) | Create a user account (FR1.1) |
| POST | `/wallets` | (verify) | Open a wallet for an account (FR1.1) |
| POST | `/wallets/{id}/deposit` | (verify) | Simulate a deposit (FR1.2) |
| POST | `/wallets/{id}/withdraw` | (verify) | Simulate a withdrawal (FR1.2) |
| GET | `/wallets/{id}` | (verify) | Read wallet balance |

## Transfer — `/transfers`  *(spec — not yet implemented)*

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/transfers` | (verify) | P2P transfer between two wallets (FR1.3) |
| GET | `/wallets/{id}/transactions` | (verify) | Filtered transaction history (FR1.4) |

### `POST /transfers` — payload sketch (spec only)

Request body:
```json
{
  "from_wallet": "string",
  "to_wallet":   "string",
  "amount":      "decimal"
}
```
Required header: `Idempotency-Key: <uuid>` (NFR3).

Response 200 (shape verify against actual DTO):
```json
{
  "transaction_id": "uuid",
  "status": "COMMITTED",
  "from_wallet": "string",
  "to_wallet": "string",
  "amount": "decimal",
  "committed_at": "iso8601"
}
```

Response 4xx (failure modes inferred from the spec — verify against the implementation):

| Status | Error key (verify) | When |
|---|---|---|
| 400 | `INVALID_PAYLOAD` | non-positive amount, malformed body |
| 400 | `SAME_WALLET` | `from_wallet == to_wallet` |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | header missing or malformed |
| 404 | `WALLET_NOT_FOUND` | sender or receiver wallet does not exist |
| 409 | `INSUFFICIENT_FUNDS` | post-debit balance would be negative |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | same key, different payload |

## Admin — `/admin`  *(spec — not yet implemented)*

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/admin/metrics` | admin (verify) | Live transaction count and total volume for the day (FR3.1) |

## Real-time channel — WebSocket  *(spec — not yet implemented)*

| Operation | Path | Auth | Purpose |
|---|---|---|---|
| WS subscribe | `/ws/admin/alerts` (verify) | admin | Receive fraud alerts pushed from the `fraud-alerts` Kafka topic (FR3.2) |

Alert payload sketch (spec only):
```json
{
  "alert_id":   "uuid",
  "rule":       "VELOCITY | VOLUME",
  "wallet_id":  "string",
  "window":     { "from": "iso8601", "to": "iso8601" },
  "evidence":   { "count": 6, "total_amount": 51230.50 },
  "raised_at":  "iso8601"
}
```
