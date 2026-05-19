/**
 * Wallet module — FR1.2, FR1.3, FR1.4 (deposit, withdraw, transfer, statement).
 * Hybrid concurrency (NFR1): Redis lock outside, DB PESSIMISTIC_WRITE inside, outbox-only publish (NFR2).
 * Layering per .claude/rules/backend_coding.md §1 — subpackages api/, service/, persistence/, event/.
 */
package com.digitalwallet.wallet;
