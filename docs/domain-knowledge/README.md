# Domain Knowledge

## §1 What the product is

Digital Wallet is a small-scale internal-funds platform: users hold a wallet, deposit and withdraw simulated funds, and transfer to other users by user ID or account number. Every successful transaction is published as an event so a separate fraud engine can score it asynchronously. Suspect activity surfaces as live alerts on an administrator dashboard.

The product exists to demonstrate two things end-to-end: ACID-correct money movement under concurrent load, and decoupled real-time risk analysis that does not slow the transaction path.

## §2 Core domain concepts

| Concept | Meaning in this product |
|---|---|
| Account | A user identity. Holds one or more wallets. |
| Wallet | A balance-bearing record owned by an account. The unit of locking during a transfer. |
| Transaction | A single deposit, withdrawal, or transfer leg. Append-only in `transaction_history`. |
| Transfer | A two-leg operation: debit sender wallet, credit receiver wallet, atomically. |
| Idempotency Key | A client-supplied UUID on transfer requests; guarantees a retry never double-debits. |
| Transaction Event | A JSON message published to `transaction-events` after a successful commit. |
| Fraud Rule | A predicate the consumer evaluates over a recent window of events for one account. |
| Fraud Alert | A message published to `fraud-alerts` and broadcast to admin dashboards. |

## §3 Typical user journeys

### Wallet user
1. Creates an account and opens a wallet (FR1.1).
2. Deposits simulated funds (FR1.2).
3. Initiates a transfer to another user — the client generates an `Idempotency-Key` and may safely retry on a network error (NFR3).
4. Reviews transaction history filtered by date range and type (FR1.4).

### Administrator
1. Opens the live dashboard.
2. Watches the running counters: total transactions today, total volume today (FR3.1).
3. Receives toast notifications the moment a fraud rule fires; clicks through for details (FR3.2).

## §4 What this product is NOT

- Not a payment processor — there is no integration with real banks, card networks, or settlement.
- Not multi-currency — currency conversion and FX are out of scope (verify; the spec is silent).
- Not a customer-facing public service — the admin role and the fraud-alert UI imply an internal/operator audience.
- Not an authoritative ledger — `transaction_history` is sufficient for the demo but is not designed for double-entry accounting.
- Not a KYC/compliance system — the fraud rules are heuristic, not regulatory.
