# Fraud Rules

The fraud engine is a Kafka consumer subscribed to `transaction-events`. It evaluates each event against the rules below and, on a violation, publishes a payload to `fraud-alerts`. See [../../README.md §3 FR2](../../README.md) and [§5 step 5](../../README.md).

> Both rules are **server-only** — they are not enforced on the request path (NFR5). A rule firing produces an *alert*, not a transfer rejection.

## Velocity — more than 5 transactions in 60 seconds

- **Rule**: if a single wallet (or account — spec ambiguous; verify) initiates more than **5** transactions within a rolling **60-second** window, raise a `VELOCITY` alert.
- **Why**: rapid-fire transfers are a classic indicator of automated abuse or account takeover.
- **Enforced in**: fraud consumer rule module (verify), using a Redis sorted-set or in-memory window counter keyed by wallet (verify).
- **Failure mode**: alert published to `fraud-alerts`; the originating transfer is **not** rolled back.
- **Frontend shortcut**: none.

## Volume — cumulative > $50,000 in 60 minutes

- **Rule**: if a single wallet's outbound transfers cumulatively exceed **$50,000** within a rolling **3,600-second** window, raise a `VOLUME` alert.
- **Why**: structuring (many small transfers totalling a large amount) evades single-transaction thresholds.
- **Enforced in**: fraud consumer rule module (verify).
- **Failure mode**: alert published to `fraud-alerts`; the originating transfer is **not** rolled back.
- **Frontend shortcut**: none.

## Alert delivery to admin clients

- **Rule**: every message on `fraud-alerts` must be broadcast to all currently-connected admin WebSocket clients.
- **Why**: FR3.2 — the admin sees the alert without a page reload.
- **Enforced in**: WebSocket controller subscribed to `fraud-alerts` (verify).
- **Failure mode**: a broker outage drops alerts; this is acceptable for the demo (open: replay strategy).
- **Frontend shortcut**: client renders a red toast on receipt.

## Configuration

The thresholds and windows above are spec defaults. Externalize them as env vars (see [../architecture/README.md §7](../architecture/README.md)) so they can be tuned without a redeploy.
